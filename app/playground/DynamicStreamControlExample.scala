package playground

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, DelayOverflowStrategy, KillSwitches, SharedKillSwitch}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * KillSwitch allows completion of graphs of FlowShape from outside.
  */
class DynamicStreamControlExample {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // ===================================================================================================================
  // UniqueKillSwitch - shutdown allows the completion of one materialized Graph after invocation. UniqueKillSwitch is
  //                    always a result of materialization.
  // ===================================================================================================================
  val countingSource1: Source[Int, NotUsed] = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
  val seqSink1: Sink[Int, Future[Seq[Int]]] = Sink.seq[Int]

  val (uniqueKillSwitch1, future1) =
    countingSource1
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(seqSink1)(Keep.both)
      .run()

  Thread.sleep(2000)

  uniqueKillSwitch1.shutdown()

  val r1: Seq[Int] = Await.result(future1, Duration.Inf)
  println(">>>>>>> r1 " + r1)
  println
  println

  // ===================================================================================================================
  // UniqueKillSwitch - abort fails the Graph. UniqueKillSwitch is always the result of materialization.
  // ===================================================================================================================
  val countingSource2: Source[Int, NotUsed] = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
  val seqSink2: Sink[Int, Future[Seq[Int]]] = Sink.seq[Int]

  val (uniqueKillSwitch2, future2) =
    countingSource2
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(seqSink2)(Keep.both)
      .run()

  Thread.sleep(2000)

  val error1 = new RuntimeException("boom!")
  uniqueKillSwitch2.abort(error1)

  val r2 = Await.result(future2.failed, Duration.Inf)
  println(">>>>>>> r2 " + r2)
  println
  println

  // ===================================================================================================================
  // SharedKillSwitch - all Graphs linked to SharedKillSwitch can be controlled by it - shutdown allows completion of
  //                    all materialized Graphs linked to it.
  // ===================================================================================================================
  val countingSource3: Source[Int, NotUsed] = Source(Stream.from(1)).delay(1.second, DelayOverflowStrategy.backpressure)
  val seqSink3: Sink[Int, Future[Seq[Int]]] = Sink.seq[Int]
  val sharedKillSwitch1: SharedKillSwitch = KillSwitches.shared("my-kill-switch")

  val future3: Future[Seq[Int]] =
    countingSource3
      .via(sharedKillSwitch1.flow)
      .runWith(seqSink3)

  val future4: Future[Seq[Int]] =
    countingSource3
      .delay(1.second, DelayOverflowStrategy.backpressure)
      .via(sharedKillSwitch1.flow)
      .runWith(seqSink3)

  Thread.sleep(3000)

  sharedKillSwitch1.shutdown()

  val r3 = Await.result(future3, Duration.Inf)
  val r4 = Await.result(future4, Duration.Inf)
  println(">>>>>>> r3 " + r3)
  println
  println
  println(">>>>>>> r4 " + r4)
  println
  println

  // ===================================================================================================================
  // SharedKillSwitch - all Graphs linked to SharedKillSwitch can be controlled by it - abort fails all Graphs.
  // ===================================================================================================================
  val countingSource4: Source[Int, NotUsed] = Source(Stream.from(1)).delay(1.second)
  val seqSink4: Sink[Int, Future[Seq[Int]]] = Sink.seq[Int]
  val sharedKillSwitch2: SharedKillSwitch = KillSwitches.shared("my-kill-switch")

  val future5 = countingSource4.via(sharedKillSwitch2.flow).runWith(seqSink4)
  val future6 = countingSource4.via(sharedKillSwitch2.flow).runWith(seqSink4)

  val error2 = new RuntimeException("boom!")
  sharedKillSwitch2.abort(error2)

  val r5 = Await.result(future5.failed, Duration.Inf)
  val r6 = Await.result(future6.failed, Duration.Inf)

  println(">>>>>>> r5 " + r5)
  println
  println
  println(">>>>>>> r6 " + r6)
  println
  println

}
