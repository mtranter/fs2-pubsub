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

import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage

trait ToPubSubMessage[ T] {
  def to(t: T): PubsubMessage
}

object ToPubSubMessage extends LowPriorityToPubSubMessageImplicits {
  def apply[F[_], T](implicit psm: ToPubSubMessage[T]): ToPubSubMessage[T] = psm

}

trait LowPriorityToPubSubMessageImplicits {

  implicit def fromPubSubMessage: ToPubSubMessage[PubsubMessage] =
    new ToPubSubMessage[PubsubMessage] {
      override def to(t: PubsubMessage): PubsubMessage = t
    }

  implicit def fromSerializerFromPubSubMessage[ T](implicit ser: Serializer[T]): ToPubSubMessage[T] =
    new ToPubSubMessage[T] {
      override def to(t: T): PubsubMessage = PubsubMessage(ByteString.copyFrom(ser.serialize(t)))
    }
}
