/*
 *  Copyright 2012-2014 Comcast Cable Communications Management, LLC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.comcast.xfinity.sirius.api.impl.compat

import scala.concurrent.{Await, ExecutionContext, Future => AkkaFuture}
import scala.concurrent.duration._
import java.util.concurrent.{ExecutionException, Executor, TimeUnit, TimeoutException}

import com.comcast.xfinity.sirius.api.SiriusFuture
import scala.concurrent.ExecutionContext.Implicits.global

import scala.language.postfixOps

/**
 * Class encapsulating a {@link akka.dispatch.Future} in a
 * {@link java.util.concurrent.Future}
 *
 * @param akkaFuture the {@see akka.dispatch.Future} to wrap
 */
class AkkaFutureAdapter[T](akkaFuture: AkkaFuture[T]) extends SiriusFuture[T] {

  /**
   * Not implemented, you may not cancel an Akka Future
   */
  def cancel(mayInterrupt: Boolean): Boolean =
    throw new IllegalStateException("Not implemented")

  /**
   * Always returns true, since this is not cancellable
   *
   * @return true always
   */
  def isCancelled: Boolean = false // if not cancellable can't be cancelled

  /**
   * {@inheritDoc}
   */
  def isDone: Boolean = akkaFuture.isCompleted

  /**
   * {@inheritDoc}
   */
  @throws(classOf[ExecutionException])
  @throws(classOf[InterruptedException])
  def get: T =
    try {
      // there is still no way in hell this can time out
      Await.result(akkaFuture, 7 days)
    } catch {
      case ie: InterruptedException => throw ie
      case e: Throwable => throw new ExecutionException(e)
    }

  /**
   * {@inheritDoc}
   */
  @throws(classOf[ExecutionException])
  @throws(classOf[TimeoutException])
  @throws(classOf[InterruptedException])
  def get(l: Long, timeUnit: TimeUnit): T =
    try {
      Await.result(akkaFuture, timeUnit.toMillis(l) milliseconds)
    } catch {
      case te: TimeoutException => throw te
      case ie: InterruptedException => throw ie
      case e: Throwable => throw new ExecutionException(e)
    }

  def addListener(runnable: Runnable): Unit =
    akkaFuture.onComplete(_ => runnable.run())

  def addListener(runnable: Runnable, executor: Executor): Unit =
    akkaFuture.onComplete(_ => runnable.run())(ExecutionContext.fromExecutor(executor))
}
