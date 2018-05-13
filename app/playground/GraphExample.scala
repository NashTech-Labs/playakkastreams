package playground

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class GraphExample {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

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
  val topHeadSink = Sink.head[Int]
  val bottomHeadSink = Sink.head[Int]
  val sharedDoubler = Flow[Int].map(_ * 2)

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

  val r1 = Await.result(future1, Duration.Inf)
  val r2 = Await.result(future1, Duration.Inf)

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

  val resultSink = Sink.head[Int]

  val runnableGraph3: RunnableGraph[Future[Int]] =
    RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit b: GraphDSL.Builder[Future[Int]] => sink =>
      import GraphDSL.Implicits._

      val pm3: UniformFanInShape[Int, Int] = b.add(pickMaxOfThree)

      Source.single(1) ~> pm3.in(0)
      Source.single(2) ~> pm3.in(1)
      Source.single(3) ~> pm3.in(2)
      pm3.out ~> sink.in

      ClosedShape
    })

  val r3 = Await.result(runnableGraph3.run(), Duration.Inf)

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
  val r4 = Await.result(future, Duration.Inf)
  println(">>>>>>> r4 " + r4)

  println
  println


}
