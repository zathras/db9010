
import com.jovial.db9010.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

object People : Table("People") {

    val id = TableColumn(this, "id", "INT AUTO_INCREMENT", Types.sqlInt)
    val name = TableColumn(this, "name", "VARCHAR(50) NOT NULL", Types.sqlString)
    val worried = TableColumn(this, "worried", "BOOLEAN NOT NULL", Types.sqlBoolean)

    override val primaryKeys = listOf(id)
}

fun main() {
    // Send logging to stdout so we can see the statements
    val logger = Logger.getLogger("com.jovial.db9010")
    val handler = object : Handler() {
        override fun publish(r: LogRecord) = println(r.message)
        override fun flush() { }
        override fun close() { }
    }
    logger.addHandler(handler)
    logger.setLevel(Level.FINE)

    // Run the bigger demo first, since it generates a bunch of output
    biggerDemo()

    // Now do the short demo, from the overview:
    Database.withConnection("jdbc:h2:mem:test", "root", "") { db ->

        db.createTable(People)

        db.statement().qualifyColumnNames(false).doNotCache() +
            "CREATE INDEX " + People + "_name on " +
            People + "(" + People.name + ")" run {}

        People.apply {
            db.insertInto(this) run { row ->
                row[name] = "Alfred E. Neuman"
                row[worried] = false
            }
        }

        val param = Parameter(Types.sqlString)
        db.select(People.columns).from(People) + "WHERE " + People.name + " <> " + param run { query ->
            query[param] = "Bob Dobbs"      // Hide Bob, if he's there
            while (query.next()) {
                val idValue = query[People.id];  // It's type-safe
                println("id:  ${idValue}")
                println("name:  ${query[People.name]}")
                println("worried?  ${query[People.worried]}")
            }
        }
        db.dropTable(People)
    }
}
