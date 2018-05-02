package controllers

import javax.inject.{Inject, Singleton}

import akka.stream.scaladsl.{Concat, Source}
import akka.util.ByteString
import models.CustomerRepository
import play.api.http.HttpEntity
import play.api.mvc._

@Singleton
class CustomerController @Inject()(cc: ControllerComponents,
                                   customerRepository: CustomerRepository) extends AbstractController(cc) {

  def customers: Action[AnyContent] =
    Action { implicit request =>
      val customerDatabasePublisher = customerRepository.customers
      val customerSource = Source.fromPublisher(customerDatabasePublisher)

      val headerCSVSource = Source.single(ByteString(""""First Name","Last Name","Email"""" + "\n"))
      val customerCSVSource =
        customerSource.map(data => ByteString(s""""${data.firstName}","${data.lastName}","${data.email}"""" + "\n"))

      val csvSource = Source.combine(headerCSVSource, customerCSVSource)(Concat[ByteString])

      // Chunked transfer encoding is only supported for HTTP/1.1
      // Ok.chunked(csvSource)

      // Sends the content as close-delimited which enables backward compatibility with HTTP/1.0
      Result(
        header = ResponseHeader(OK, Map(CONTENT_DISPOSITION → s"attachment; filename=customers.csv")),
        body = HttpEntity.Streamed(csvSource, None, None))
    }

}
