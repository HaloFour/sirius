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

import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Future => AkkaFuture}
import akka.actor.ActorSystem
import java.io.IOException
import java.util.concurrent.{ExecutionException, Executor, TimeUnit, TimeoutException}

import akka.dispatch.ExecutionContexts
import akka.util.Timeout
import com.comcast.xfinity.sirius.NiceTest

import scala.concurrent.duration._
import org.mockito.Mockito.{never, timeout, verify}
import org.mockito.Matchers.any

class AkkaFutureAdapterTest extends NiceTest with BeforeAndAfterAll {

  implicit val as = ActorSystem("AkkaFutureAdapterTest")
  implicit val ec = ExecutionContexts.global()

  override def afterAll {
    Await.result(as.terminate, Duration.Inf)
  }

  describe("AkkaFutureAdapter") {
    it("must throw an IllegalStateException when cancel is called") {
      intercept[IllegalStateException] {
        val akkaFuture = AkkaFuture { "foo" }
        new AkkaFutureAdapter[String](akkaFuture).cancel(true)
      }
    }

    it("must return the value expected on get") {
      assertResult("foo") {
        val akkaFuture = AkkaFuture { "foo" }
        new AkkaFutureAdapter[String](akkaFuture).get(2, TimeUnit.SECONDS)
      }
    }

    it("must invoke Runnable callback when already done") {
      val akkaFuture = AkkaFuture.successful("foo")
      val runnable = mock[Runnable]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable)
      verify(runnable, timeout(100).times(1)).run()
      assert(adapter.isDone)
    }

    it("must invoke Runnable callback when already failed") {
      val akkaFuture = AkkaFuture.failed(new Exception("FAILED"))
      val runnable = mock[Runnable]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable)
      verify(runnable, timeout(100).times(1)).run()
      assert(adapter.isDone)
    }

    it("must invoke Runnable callback when already done on provided Executor") {
      val akkaFuture = AkkaFuture.successful("foo")
      val runnable = mock[Runnable]
      val executor = mock[Executor]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable, executor)
      verify(executor, timeout(100).times(1)).execute(any[Runnable])
      assert(adapter.isDone)
    }

    it("must invoke Runnable callback when already failed on provided Executor") {
      val akkaFuture = AkkaFuture.failed(new Exception("FAILED"))
      val runnable = mock[Runnable]
      val executor = mock[Executor]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable, executor)
      verify(executor, timeout(100).times(1)).execute(any[Runnable])
      assert(adapter.isDone)
    }

    it("must invoke Runnable callback when eventually done") {
      val akkaFuture = AkkaFuture { Thread.sleep(500); "foo" }
      val runnable = mock[Runnable]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable)
      verify(runnable, never()).run()
      Await.ready(akkaFuture, Timeout(1 second).duration)
      verify(runnable, timeout(100).times(1)).run()
      assert(adapter.isDone)
    }

    it("must invoke Runnable callback when eventually failed") {
      val akkaFuture = AkkaFuture { Thread.sleep(500); throw new Exception("FAILED") }
      val runnable = mock[Runnable]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable)
      verify(runnable, never()).run()
      Await.ready(akkaFuture, Timeout(1 second).duration)
      verify(runnable, timeout(100).times(1)).run()
      assert(adapter.isDone)
    }

    it("must invoke Runnable callback when eventually done on provided Executor") {
      val akkaFuture = AkkaFuture { Thread.sleep(500); "foo" }
      val runnable = mock[Runnable]
      val executor = mock[Executor]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable, executor)
      verify(executor, never()).execute(any[Runnable])
      Await.ready(akkaFuture, Timeout(1 second).duration)
      verify(executor, timeout(100).times(1)).execute(any[Runnable])
      assert(adapter.isDone)
    }

    it("must invoke Runnable callback when eventually failed on provided Executor") {
      val akkaFuture = AkkaFuture { Thread.sleep(500); throw new Exception("FAILED") }
      val runnable = mock[Runnable]
      val executor = mock[Executor]
      val adapter = new AkkaFutureAdapter[String](akkaFuture)
      adapter.addListener(runnable, executor)
      verify(executor, never()).execute(any[Runnable])
      Await.ready(akkaFuture, Timeout(1 second).duration)
      verify(executor, timeout(100).times(1)).execute(any[Runnable])
      assert(adapter.isDone)
    }

    it("must throw a TimeoutException if it takes too long") {
      intercept[TimeoutException] {
        val akkaFuture = AkkaFuture { Thread.sleep(1000); "foo" }
        new AkkaFutureAdapter[String](akkaFuture).get(500, TimeUnit.MILLISECONDS)
      }
    }

    it("must propogate an exception as an ExecutionException") {
      intercept[ExecutionException] {
        val akkaFuture = AkkaFuture { throw new IOException("Boom") }
        new AkkaFutureAdapter[String](akkaFuture).get()
      }
    }
  }

}
