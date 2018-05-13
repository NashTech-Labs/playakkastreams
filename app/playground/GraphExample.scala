package playground

import java.nio.ByteOrder

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class GraphExample {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // ===================================================================================================================
  // Runnable graph - NotUsed print to console
  // ===================================================================================================================
  val closedShapeGraphNotUsed: Graph[ClosedShape, NotUsed] =
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val in = Source(List(1, 1, 1))
      val out = Sink.foreach(println)

      val bcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))
      val merge: UniformFanInShape[Int, Int] = builder.add(Merge[Int](2))

      val f1, f2, f3, f4 = Flow[Int].map(_ + 10)

      in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
      bcast ~> f4 ~> merge

      ClosedShape
    }

  val runnableGraph1: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(closedShapeGraphNotUsed)
  runnableGraph1.run()
  println
  println

  // ===================================================================================================================
  // Runnable graph - Source to double sinks
  // ===================================================================================================================
  val topHeadSink: Sink[Int, Future[Int]] = Sink.head[Int]
  val bottomHeadSink: Sink[Int, Future[Int]] = Sink.head[Int]
  val sharedDoubler: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 2)

  val doubleSinkClosedShapeGraph: Graph[ClosedShape, (Future[Int], Future[Int])] =
    GraphDSL
      .create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder: GraphDSL.Builder[(Future[Int], Future[Int])] =>
        (topHS, bottomHS) =>
          import GraphDSL.Implicits._

          val source = Source.single(1)
          val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2))

          source ~> broadcast

          broadcast ~> sharedDoubler ~> topHS
          broadcast ~> sharedDoubler ~> bottomHS

          ClosedShape
      }

  val runnableGraph2: RunnableGraph[(Future[Int], Future[Int])] = RunnableGraph.fromGraph(doubleSinkClosedShapeGraph)
  val (future1, future2) = runnableGraph2.run()

  val r1: Int = Await.result(future1, Duration.Inf)
  val r2: Int = Await.result(future1, Duration.Inf)
  println(">>>>>>> r1 " + r1)
  println(">>>>>>> r2 " + r2)
  println
  println

  // ===================================================================================================================
  // Runnable graph - with 3 inputs and one output which finds the max of 3 inputs
  // ===================================================================================================================
  val pickMaxOfThree: Graph[UniformFanInShape[Int, Int], NotUsed] =
    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // ZipWith's last type is output
      val zip1: FanInShape2[Int, Int, Int] = b.add(ZipWith[Int, Int, Int](math.max))
      val zip2: FanInShape2[Int, Int, Int] = b.add(ZipWith[Int, Int, Int](math.max))

      zip1.out ~> zip2.in0

      UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
    }

  val resultSink: Sink[Int, Future[Int]] = Sink.head[Int]

  val runnableGraph3: RunnableGraph[Future[Int]] =
    RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit b: GraphDSL.Builder[Future[Int]] =>
      sink =>
        import GraphDSL.Implicits._

        val pm3: UniformFanInShape[Int, Int] = b.add(pickMaxOfThree)

        Source.single(1) ~> pm3.in(0)
        Source.single(2) ~> pm3.in(1)
        Source.single(3) ~> pm3.in(2)
        pm3.out ~> sink.in

        ClosedShape
    })

  val r3: Int = Await.result(runnableGraph3.run(), Duration.Inf)
  println(">>>>>>> r3 " + r3)
  println
  println

  // ===================================================================================================================
  // Runnable graph - 2 sources zipped into tuple
  // ===================================================================================================================
  val pairsSource: Source[(Int, Int), NotUsed] =
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val zip: FanInShape2[Int, Int, (Int, Int)] = b.add(Zip[Int, Int]())

      def ints: Source[Int, NotUsed] =
        Source.fromIterator(() => Iterator.from(1))

      ints.filter(_ % 2 != 0) ~> zip.in0
      ints.filter(_ % 2 == 0) ~> zip.in1

      SourceShape(zip.out)
    })

  val firstPair: (Int, Int) = Await.result(pairsSource.runWith(Sink.head), Duration.Inf)
  println(">>>>>>> firstPair " + firstPair)
  println
  println

  // ===================================================================================================================
  // Runnable graph - sink?
  // =--==================================================================================================================
  /*val testSink =
    Sink.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val println = b.add(Zip[Int, Int]())


      SinkShape(println.in0)
    })

  Source.single(1).runWith(testSink)*/

  // ===================================================================================================================
  // Runnable graph - Int to (Int, String)
  // ===================================================================================================================
  val pairUpWithToString: Flow[Int, (Int, String), NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val bcast: UniformFanOutShape[Int, Int] = b.add(Broadcast[Int](2))
      val zip: FanInShape2[Int, String, (Int, String)] = b.add(Zip[Int, String]())

      bcast.out(0) ~> zip.in0
      bcast.out(1).map(_.toString) ~> zip.in1

      FlowShape(bcast.in, zip.out)
    })

  val (_, future) = pairUpWithToString.runWith(Source.single(1), Sink.head)
  val r4: (Int, String) = Await.result(future, Duration.Inf)
  println(">>>>>>> r4 " + r4)
  println
  println

  // ===================================================================================================================
  // Simplified API for merging 2 sources
  // ===================================================================================================================
  val sourceOne = Source(List(1))
  val sourceTwo = Source(List(2))

  val mergedSource: Source[Int, NotUsed] = Source.combine(sourceOne, sourceTwo)(Merge(_))
  println(">>>>>>> mergedSource")
  mergedSource.runForeach(println)
  println
  println

  // ===================================================================================================================
  // Simplified API for combining 2 sinks
  // ===================================================================================================================
  val remoteProcessing: Sink[Int, Future[Done]] = Sink.foreach[Int](e => println(s"Processing remotely $e"))
  val localProcessing: Sink[Int, Future[Done]] = Sink.foreach[Int](e => println(s"Processing locally $e"))

  val combinedSink: Sink[Int, NotUsed] = Sink.combine(remoteProcessing, localProcessing)(Broadcast[Int](_))

  Source(List(1, 2)).runWith(combinedSink)
  println
  println

  // ===================================================================================================================
  // Bidirectional Flow - codec example - serialize Message to ByteString and de-serialize ByteString to Message
  // ===================================================================================================================
  trait Message
  case class Ping(id: Int) extends Message
  case class Pong(id: Int) extends Message

  def toBytes(msg: Message): ByteString = {
    implicit val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN

    msg match {
      case Ping(id) => ByteString.newBuilder.putByte(1).putInt(id).result()
      case Pong(id) => ByteString.newBuilder.putByte(2).putInt(id).result()
    }
  }

  def fromBytes(bytes: ByteString): Message = {
    implicit val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN

    val it = bytes.iterator

    it.getByte match {
      case 1 => Ping(it.getInt)
      case 2 => Pong(it.getInt)
      case _ => throw new RuntimeException("Auh!")
    }
  }

  val codec: BidiFlow[Message, ByteString, ByteString, Message, NotUsed] = BidiFlow.fromFunctions(toBytes, fromBytes)

  // ===================================================================================================================
  // Accessing materialized value inside the Graph
  // ===================================================================================================================
  val foldFlow: Flow[Int, Int, Future[Int]] =
    Flow.fromGraph(GraphDSL.create(Sink.fold[Int, Int](0)(_ + _)) { implicit b: GraphDSL.Builder[Future[Int]] =>
      fold: SinkShape[Int] =>
        import GraphDSL.Implicits._

        FlowShape(fold.in, b.materializedValue.mapAsync(4)(identity).outlet)
    })

  val runnableGraph4: RunnableGraph[Future[Int]] =
    Source(List(1, 2)).viaMat(foldFlow)(Keep.right).to(Sink.foreach(e => println(s">>>>>>> foldFlow $e")))
  runnableGraph4.run()

  // ===================================================================================================================
  // Graph cycle - deadlock - all processing stops after some time
  // ===================================================================================================================
  val deadlockRunnableGraph: RunnableGraph[NotUsed] =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val merge = b.add(Merge[Int](2))
      val bcast = b.add(Broadcast[Int](2))

      val source = Source.fromIterator(() => Iterator.from(1))

      source ~> merge ~> Flow[Int].map { s => println(s"Feedback graph with deadlock $s"); s } ~> bcast ~> Sink.ignore
      merge <~ bcast

      ClosedShape
    })

  // deadlockRunnableGraph.run()

  // ===================================================================================================================
  // Graph cycle - is not a deadlock because the source of elements is the feedback arc as preferred source. Though the
  //               source remain back-pressured forever.
  // ===================================================================================================================
  val notDeadlockRunnableGraph: RunnableGraph[NotUsed] =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val merge = b.add(MergePreferred[Int](1))
      val bcast = b.add(Broadcast[Int](2))

      val source = Source.fromIterator(() => Iterator.from(1))

      source ~> merge ~> Flow[Int].map { s => println(s"Feedback graph without deadlock $s"); s } ~> bcast ~> Sink.ignore
      merge.preferred <~ bcast

      ClosedShape
    })

  // notDeadlockRunnableGraph.run()

  // ===================================================================================================================
  // Graph cycle - the feedback loop elements are dropped if buffer increases after specified limit but the source
  //               elements are continuously printed on the console hence not constantly back-pressured.
  // ===================================================================================================================
  val droppingHeadRunnableGraph: RunnableGraph[NotUsed] =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val merge = b.add(Merge[Int](2))
      val bcast = b.add(Broadcast[Int](2))

      val source = Source.fromIterator(() => Iterator.from(1))

      source ~> merge ~> Flow[Int].map { s => println(s"Feedback graph with dropping head $s"); s } ~> bcast ~> Sink.ignore
      merge <~ Flow[Int].buffer(10, OverflowStrategy.dropHead) <~ bcast

      ClosedShape
    })

  // droppingHeadRunnableGraph.run()

  // ===================================================================================================================
  // Graph cycle - the feedback loop is balanced from the start because ZipWith takes one element from Source and one
  //               from feedback arc. But this does not output anything on the console because no right is present
  // ===================================================================================================================
  val zippedRunnableGraph: RunnableGraph[NotUsed] =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val zip = b.add(ZipWith[Int, Int, Int]((left, right) => right))
      val bcast = b.add(Broadcast[Int](2))

      val source = Source.fromIterator(() => Iterator.from(1))

      source ~> zip.in0
      zip.out.map { s => println(s"Zipped feedback graph $s"); s } ~> bcast ~> Sink.ignore
      zip.in1 <~ bcast

      ClosedShape
    })

  // zippedRunnableGraph.run()

  // ===================================================================================================================
  // Graph cycle - the feedback loop is balanced from the start because ZipWith takes one element from Source and one
  //               from feedback arc. Unlike above the console prints elements as the stream was initiated.
  // ===================================================================================================================
  val zippedRunnableGraphWithStart: RunnableGraph[NotUsed] =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val zip = b.add(ZipWith[Int, Int, Int]((left, right) => right))
      val bcast = b.add(Broadcast[Int](2))
      val concat = b.add(Concat[Int]())

      val start = Source.single(1)
      val source = Source.fromIterator(() => Iterator.from(2))

      source ~> zip.in0
      zip.out.map { s => println(s"Zipped feedback graph with start $s"); s } ~> bcast ~> Sink.ignore
      zip.in1 <~ concat <~ start
      concat <~ bcast

      ClosedShape
    })

  // zippedRunnableGraphWithStart.run()

}
