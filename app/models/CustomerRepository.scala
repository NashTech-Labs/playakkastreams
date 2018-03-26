package models

import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.basic.DatabasePublisher
import slick.jdbc.{ResultSetConcurrency, ResultSetType}
import slick.lifted.ProvenShape

/**
  * CREATE TABLE customers (
  * id           BIGSERIAL PRIMARY KEY,
  * firstname    VARCHAR(255) NOT NULL,
  * lastname     VARCHAR(255) NOT NULL,
  * email        VARCHAR(255) NOT NULL
  * );
  */
case class Customer(id: Long,
                    firstName: String,
                    lastName: String,
                    email: String)

class CustomerRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends CustomerTable {

  import profile.api._

  def customers: DatabasePublisher[Customer] =
    db.stream(
      customerQuery
        .result
        .withStatementParameters(
          rsType = ResultSetType.ForwardOnly,
          rsConcurrency = ResultSetConcurrency.ReadOnly,
          fetchSize = 10000)
        .transactionally)

}

trait CustomerTable extends HasDatabaseConfigProvider[slick.jdbc.JdbcProfile] {

  import profile.api._

  val customerQuery: TableQuery[CustomerMapping] = TableQuery[CustomerMapping]

  private[models] class CustomerMapping(tag: Tag) extends Table[Customer](tag, "customers") {

    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def firstName: Rep[String] = column[String]("firstname")

    def lastName: Rep[String] = column[String]("lastname")

    def email: Rep[String] = column[String]("email")

    def * : ProvenShape[Customer] =
      (id, firstName, lastName, email) <> (Customer.tupled, Customer.unapply)

  }

}
