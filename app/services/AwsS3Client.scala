package services

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.s3.impl.ListBucketVersion2
import akka.stream.alpakka.s3.scaladsl.{MultipartUploadResult, S3Client}
import akka.stream.alpakka.s3.{MemoryBufferType, S3Settings}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.AwsRegionProvider

import scala.concurrent.Future

@Singleton
class AwsS3Client @Inject()(system: ActorSystem, materializer: Materializer) {

  private val awsCredentials = new BasicAWSCredentials("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY")
  private val awsCredentialsProvider = new AWSStaticCredentialsProvider(awsCredentials)
  private val regionProvider =
    new AwsRegionProvider {
      def getRegion: String = "us-west-2"
    }
  private val settings = new S3Settings(MemoryBufferType, None, awsCredentialsProvider, regionProvider, false, None, ListBucketVersion2)
  private val s3Client = new S3Client(settings)(system, materializer)

  def s3Sink(bucketName: String, bucketKey: String): Sink[ByteString, Future[MultipartUploadResult]] =
    s3Client.multipartUpload(bucketName, bucketKey)

}
