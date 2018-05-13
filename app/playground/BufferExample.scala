package playground

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, Attributes, ClosedShape}

class BufferExample {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // ===================================================================================================================
  // Running stages synchronously
  // ===================================================================================================================
  Source(1 to 3)
    .map { i => println(s"A sync: $i"); i }
    .map { i => println(s"B sync: $i"); i }
    .map { i => println(s"C sync: $i"); i }
    .runWith(Sink.ignore)

  println
  println

  // ===================================================================================================================
  // Running stages asynchronously
  // ===================================================================================================================
  Source(1 to 3)
    .map { i => println(s"A async: $i"); i }.async
    .map { i => println(s"B async: $i"); i }.async
    .map { i => println(s"C async: $i"); i }.async
    .runWith(Sink.ignore)

  // ===================================================================================================================
  // Setting async boundary buffer size for specific stage
  // ===================================================================================================================
  val section =
    Flow[Int]
      .map(_ + 1)
      .async
      .withAttributes(Attributes.inputBuffer(initial = 1, max = 1)) // buffer size of this section is 1

  val anotherFlow = section.via(Flow[Int].map(_ + 1).async)
  // buffer size of the other flow is the default value, i.e 16

  // ===================================================================================================================
  // Async boundary internal buffer messing with scheduled times
  // ===================================================================================================================
  import scala.concurrent.duration._

  case class Tick()

  val scheduledRunnableGraph: RunnableGraph[NotUsed] =
    RunnableGraph.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._

      // this is the asynchronous stage in this graph
      val zipper = b.add(ZipWith[Tick, Int, Int]((tick, count) ⇒ count).async)

      Source.tick(initialDelay = 3.second, interval = 3.second, Tick()) ~> zipper.in0

      Source.tick(initialDelay = 1.second, interval = 1.second, "message!")
        .conflateWithSeed(seed = (_) ⇒ 1)((count, _) ⇒ count + 1) ~> zipper.in1

      zipper.out ~> Sink.foreach(println)
      ClosedShape
    })

  // scheduledRunnableGraph.run()

  // ===================================================================================================================
  // Understanding conflate
  // ===================================================================================================================
  val sampleFlow = Flow[Double]
    .conflateWithSeed(Seq(_)) {
      case (acc, elem) if 1 < 2 ⇒ acc :+ elem
      case (acc, _)             ⇒ acc
    }

  Source((1 to 10).map(_.toDouble)).viaMat(sampleFlow)(Keep.right).to(Sink.foreach(println)).run()

}
