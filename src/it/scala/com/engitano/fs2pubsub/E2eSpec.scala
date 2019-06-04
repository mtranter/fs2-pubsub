/*
 * Copyright (c) 2019 Engitano
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.engitano.fs2pubsub

import cats.effect._
import cats.implicits._
import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.{DockerContainer, DockerFactory, DockerKit, DockerReadyChecker}
import fs2._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.ExecutionContext

class E2eSpec extends WordSpec with Matchers with DockerPubSubService with BeforeAndAfterAll {

  import HasAckId._
  import com.engitano.fs2pubsub.syntax._

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  override def beforeAll(): Unit = {
    startAllOrFail()
  }

  override def afterAll(): Unit = {
    stopAllQuietly()
  }

  "The Generated clients" should {
    "be able to read and write to PubSub" in {

      val msgCount = 2000

      implicit val intSerializer = Serializer.from[IO, Int](i => BigInt(i).toByteArray)
      implicit val intDeserializer = Deserializer.from[IO, Int](b => BigInt(b).toInt)
      val cfg = GrpcPubsubConfig.local(DefaultGcpProject, DefaultPubsubPort)

      def publisher[F[_] : ConcurrentEffect]: Resource[F, Publisher[F]] =
        Publisher[F](cfg)

      def subscriber[F[_] : ConcurrentEffect]: Resource[F, Subscriber[F]] =
        Subscriber[F](cfg)

      val topicName = "test-topic"
      val testSubscription = "test-sub"

      def setup[F[_] : ConcurrentEffect]: F[Unit] = (subscriber[F], publisher[F]).tupled.use { c =>
        for {
          _ <- c._2.createTopic(topicName)
          _ <- c._1.createSubscription(testSubscription, topicName)
        } yield ()
      }

      val program = Publisher[IO](cfg).use { implicit pub =>
        Subscriber[IO](cfg).use { implicit sub =>

          def publisher(implicit T: ToPubSubMessage[IO, Int]): Stream[IO, String] =
            Stream.emits[IO, Int](1 to msgCount)
              .toPubSub(topicName)

          def subscriber(implicit T: FromPubSubMessage[Int]): Stream[IO, PubSubResponse[Int]] =
            sub.consume[Int](testSubscription)(r => r)

          def run(implicit
                        E: ConcurrentEffect[IO],
                        T: ToPubSubMessage[IO, Int],
                        F: FromPubSubMessage[Int]): Stream[IO, PubSubResponse[Int]] = for {
            _ <- Stream.eval(setup[IO])
            ints <- subscriber.concurrently(publisher)
          } yield ints

          run
            .take(msgCount)
            .compile.toList
        }
      }

      program.attempt.unsafeRunSync() match {
        case Right(v) => v.map(_.body.right.get) shouldBe (1 to msgCount)
        case Left(value) => throw value
      }
    }
  }
}


trait DockerPubSubService extends DockerKit {

  val DefaultPubsubPort = 8085

  val DefaultGcpProject = "test-project"

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  override implicit def dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  val pubsub = DockerContainer("mtranter/gcp-pubsub-emulator:latest")
    .withPorts(DefaultPubsubPort -> Some(DefaultPubsubPort))
    .withReadyChecker(DockerReadyChecker.LogLineContains("Server started"))
    .withCommand("--project", DefaultGcpProject, "--log-http", "--host-port", s"0.0.0.0:$DefaultPubsubPort")


  abstract override def dockerContainers = pubsub :: super.dockerContainers
}