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
package com.comcast.xfinity.sirius.uberstore.data

import com.comcast.xfinity.sirius.NiceTest
import com.comcast.xfinity.sirius.api.impl.{Delete, OrderedEvent}
import org.mockito.Matchers.{any, same, eq => argEq}
import org.mockito.Mockito._

import scala.util.Random

class UberDataFileTest extends NiceTest {

  val fileName = Random.nextString(20)
  
  describe("writeEvent") {
    it ("must serialize the event and delegate its persistence to fileOps") {
      val mockWriteHandle = mock[UberDataFileWriteHandle]
      val mockFileHandleFactory = mock[UberDataFileHandleFactory]
      doReturn(mockWriteHandle).when(mockFileHandleFactory).createWriteHandle(fileName)

      val mockFileOps = mock[UberStoreFileOps]
      val mockCodec = mock[OrderedEventCodec]

      val underTest = new UberDataFile(fileName, mockFileHandleFactory, mockFileOps, mockCodec)

      val theEvent = OrderedEvent(1, 2, Delete("hello world"))
      val serialized = "i award you know points, and may god have mercy on your soul".getBytes
      doReturn(serialized).when(mockCodec).serialize(theEvent)

      doReturn(1000L).when(mockFileOps).put(argEq(mockWriteHandle), any[Array[Byte]])

      assert(1000L === underTest.writeEvent(theEvent))

      verify(mockFileOps).put(same(mockWriteHandle), same(serialized))
    }
  }

  describe("foldLeft") {
    it ("must fold over the entire content of the file, invoking fileOps.readNext until there are None, " +
        "and closes the read handle on completion") {
      val mockWriteHandle = mock[UberDataFileWriteHandle]
      val mockReadHandle = mock[UberDataFileReadHandle]
      val mockFileHandleFactory = mock[UberDataFileHandleFactory]
      doReturn(mockWriteHandle).when(mockFileHandleFactory).createWriteHandle(fileName)
      doReturn(mockReadHandle).when(mockFileHandleFactory).createReadHandle(fileName, 0L)

      val mockFileOps = mock[UberStoreFileOps]
      val mockCodec = mock[OrderedEventCodec]

      val underTest = new UberDataFile(fileName, mockFileHandleFactory, mockFileOps, mockCodec)

      // Need to simulate 3 successful reads from the handle, followed by None indicating we hit the end
      val dummyBytes = "dummy".getBytes
      doReturn(Some(dummyBytes)).doReturn(Some(dummyBytes)).doReturn(Some(dummyBytes)).doReturn(None).
        when(mockFileOps).readNext(any[UberDataFileReadHandle])
      doReturn(0L).doReturn(10L).doReturn(20L).doReturn(30L).when(mockReadHandle).offset()

      // Need to simulate the conversion of the events from above becoming OrderedEvents
      val event1 = OrderedEvent(1, 2, Delete("a"))
      val event2 = OrderedEvent(2, 3, Delete("b"))
      val event3 = OrderedEvent(3, 4, Delete("c"))
      doReturn(event1).doReturn(event2).doReturn(event3).
        when(mockCodec).deserialize(any[Array[Byte]])

      val result = underTest.foldLeft(List[(Long, OrderedEvent)]())(
        (acc, off, evt) => (off, evt) :: acc
      ).reverse

      assert(List((0L, event1), (10L, event2), (20L, event3)) === result)

      // verify that we read and deserialized the expected number of times
      verify(mockFileOps, times(4)).readNext(same(mockReadHandle))
      verify(mockCodec, times(3)).deserialize(same(dummyBytes))

      // also verify cleanup!
      verify(mockReadHandle).close()
    }
  }

  describe("foldLeftRange") {
    it ("must only iterate over the specified range of offsets, inclusive, and when finished close the handle") {
      val mockWriteHandle = mock[UberDataFileWriteHandle]
      val mockReadHandle = mock[UberDataFileReadHandle]
      val mockFileHandleFactory = mock[UberDataFileHandleFactory]
      doReturn(mockWriteHandle).when(mockFileHandleFactory).createWriteHandle(fileName)
      doReturn(mockReadHandle).when(mockFileHandleFactory).createReadHandle(fileName, 100L)

      val mockFileOps = mock[UberStoreFileOps]
      val mockCodec = mock[OrderedEventCodec]

      val underTest = new UberDataFile(fileName, mockFileHandleFactory, mockFileOps, mockCodec)

      // we will pretend we are starting at a later offset, and read a few events until we hit the end
      //  offset
      doReturn(100L).doReturn(110L).doReturn(120L).doReturn(130L).
        when(mockReadHandle).offset()

      // Need to simulate 3 successful reads from the handle, corresponding with the offsets above
      val dummyBytes = "dummy".getBytes
      doReturn(Some(dummyBytes)).doReturn(Some(dummyBytes)).doReturn(Some(dummyBytes)).
        when(mockFileOps).readNext(any[UberDataFileReadHandle])

      // Need to simulate the conversion of the events from above becoming OrderedEvents
      val event1 = OrderedEvent(1, 2, Delete("a"))
      val event2 = OrderedEvent(2, 3, Delete("b"))
      val event3 = OrderedEvent(3, 4, Delete("c"))
      doReturn(event1).doReturn(event2).doReturn(event3).
        when(mockCodec).deserialize(any[Array[Byte]])

      val result = underTest.foldLeftRange(100L, 120L)(List[(Long, OrderedEvent)]())(
        (acc, off, evt) => (off, evt) :: acc
      ).reverse

      assert(List((100L, event1), (110L, event2), (120L, event3)) === result)

      verify(mockFileOps, times(3)).readNext(same(mockReadHandle))
      verify(mockCodec, times(3)).deserialize(same(dummyBytes))

      // also verify cleanup!
      verify(mockReadHandle).close()
    }
  }

  describe ("close") {
    it ("should close provided writeHandle") {
      val mockWriteHandle = mock[UberDataFileWriteHandle]
      val mockFileHandleFactory = mock[UberDataFileHandleFactory]
      doReturn(mockWriteHandle).when(mockFileHandleFactory).createWriteHandle(fileName)

      val mockFileOps = mock[UberStoreFileOps]
      val mockCodec = mock[OrderedEventCodec]

      val underTest = new UberDataFile(fileName, mockFileHandleFactory, mockFileOps, mockCodec)
      underTest.close()

      verify(mockWriteHandle).close()
    }
  }
}
