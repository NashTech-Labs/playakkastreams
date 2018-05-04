package controllers

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import akka.stream.scaladsl._
import akka.util.ByteString
import models.CustomerRepository
import play.api.http.HttpEntity
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import services.AwsS3Client

import scala.concurrent.ExecutionContext

@Singleton
class CustomerController @Inject()(cc: ControllerComponents, customerRepository: CustomerRepository, awsS3Client: AwsS3Client)
                                  (implicit ec: ExecutionContext, mat: Materializer) extends AbstractController(cc) {

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

  def upload: Action[MultipartFormData[MultipartUploadResult]] =
    Action(parse.multipartFormData(handleFilePartAwsUploadResult)) { request =>
      val maybeUploadResult =
        request.body.file("customers").map {
          case FilePart(key, filename, contentType, multipartUploadResult) =>
            multipartUploadResult
        }

      maybeUploadResult.fold(
        InternalServerError("Something went wrong!")
      )(uploadResult =>
        Ok(s"File ${uploadResult.key} upload to bucket ${uploadResult.bucket}")
      )
    }

  private def handleFilePartAwsUploadResult: Multipart.FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, filename, contentType) =>
      val accumulator = Accumulator(awsS3Client.s3Sink("test-ocr", filename))

      accumulator map { multipartUploadResult =>
        FilePart(partName, filename, contentType, multipartUploadResult)
      }
  }

}
