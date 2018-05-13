package playground

import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ThrottleMode.Shaping
import akka.stream.{IOResult, ActorMaterializer}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise, Future}

class StreamExample {

  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()

  //====================================================================================================================
  //====================================================================================================================
  val source1: Source[Int, NotUsed] = Source(1 to 6)
  source1.runForeach(println)

  val factorials: Source[BigInt, NotUsed] = source1.scan(BigInt(1))((acc, next) => acc * next)

  val eventualIOResult: Future[IOResult] =
    factorials
      .map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("factorial.txt")))

  val eventualSumResult: Future[Int] = source1.runWith(Sink.fold(0)(_ + _))

  val runnableGraph: RunnableGraph[Future[Int]] = source1.toMat(Sink.fold(0)(_ + _))(Keep.right)
  val runnableGraphNotUsed1: RunnableGraph[NotUsed] = source1.to(Sink.foreach(println))
  val runnableGraphNotUsed2: RunnableGraph[NotUsed] = source1.via(Flow[Int].map(_ * 2)).to(Sink.foreach(println))
  val runnableGraphNotUsed3: RunnableGraph[NotUsed] = source1.to(Flow[Int].alsoTo(Sink.foreach(println(_))).to(Sink.ignore))

  //====================================================================================================================
  //====================================================================================================================
  val source2: Source[Int, Promise[Option[Int]]] = Source.maybe[Int]
  val flow: Flow[Int, Int, NotUsed] = Flow[Int].throttle(1, 1.second, 1, Shaping)
  val sink: Sink[Int, Future[Int]] = Sink.head[Int]

  // RUNNABLE GRAPH - By default the materialized value of the lest most stage is preserved
  val runnableGraphLeftStage: RunnableGraph[Promise[Option[Int]]] = source2.via(flow).to(sink)

  // RUNNABLE GRAPH - Preserve the materialized value of flow
  val runnableGraphPreserveMatOfFlow: RunnableGraph[NotUsed] = source2.viaMat(flow)(Keep.right).to(sink)

  // RUNNABLE GRAPH - Preserve the materialized value of Sink
  val runnableGraphPreserveMatOfSink: RunnableGraph[Future[Int]] = source2.via(flow).toMat(sink)(Keep.right)

  // RUNNABLE GRAPH - Preserve the materialized value of Source and Flow
  val runnableGraphMatOfSourceAndFlow: RunnableGraph[(Promise[Option[Int]], NotUsed)] =
    source2.viaMat(flow)(Keep.both).to(sink)

  // RUNNABLE GRAPH - Preserve the materialized value of Source, Flow and Sink
  val runnableGraphMatOfSourceAndSlowAndSink: RunnableGraph[((Promise[Option[Int]], NotUsed), Future[Int])] =
    source2.viaMat(flow)(Keep.both).toMat(sink)(Keep.both)

  // RUNNABLE GRAPH - Mapping over materialized value is possible
  val runnableGraphFlattened: RunnableGraph[(Promise[Option[Int]], NotUsed, Future[Int])] =
    runnableGraphMatOfSourceAndSlowAndSink.mapMaterializedValue {
      case ((promise, notUsed), future) => (promise, notUsed, future)
    }

  val (promise, notUsed, future) = runnableGraphFlattened.run()

  promise.success(Some(1))
  println(">>>>>>> notUsed " + notUsed)
  val result = Await.result(future, Duration.Inf)
  println(">>>>>>> result " + result)

  // RUNNABLE GRAPH - Preserve the materialized value of Flow and Sink
  val runnableGraphMatOfFlowAndSink: RunnableGraph[(NotUsed, Future[Int])] =
    source2.viaMat(flow)(Keep.right).toMat(sink)(Keep.both)

  // RESULT - Always returns Mat on runWith's parameter
  val resultMatOfSink: Future[Int] = source2.via(flow).runWith(sink)
  val resultMatOfSource: Promise[Option[Int]] = flow.to(sink).runWith(source2)
  val resultMatOfSourceAndSink: (Promise[Option[Int]], Future[Int]) = flow.runWith(source2, sink)

  //====================================================================================================================
  //====================================================================================================================
  def linkSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

}
