
import java.sql.PreparedStatement
import com.jovial.db9010.*

object Users : Table("Users") {

    val id = TableColumn(this, "id", "VARCHAR(10) NOT NULL", Types.sqlString)
    val name = TableColumn(this, "name", "VARCHAR(50) NOT NULL", Types.sqlString)
    val cityId = TableColumn(this, "city_id", "INT NULL", Types.sqlInt.nullable(java.sql.Types.INTEGER))

    override val primaryKeys = listOf(id)
    override val constraintsSql
        = "FOREIGN KEY (${cityId.sqlName}) REFERENCES Cities(id) ON DELETE RESTRICT ON UPDATE RESTRICT"
}

object Cities : Table("Cities") {

    val id = TableColumn(this, "id", "INT AUTO_INCREMENT", Types.sqlInt)
    val name = TableColumn(this, "name", "VARCHAR(50) NOT NULL", Types.sqlString)

    override val primaryKeys = listOf(id)
}

object CitiesCount  {
    val count = QueryColumn("COUNT(*)", Types.sqlInt)
    val maxId = QueryColumn("MAX(${Cities.id.qualifiedName})", Types.sqlInt)

    val columns = listOf(count, maxId)
    val table = Cities
}

fun biggerDemo() {
    Database.withConnection("jdbc:h2:mem:test", "root", "") { db ->
        db.transaction {
            db.createTable(Cities)
            db.createTable(Users, ifNotExists=true)
        }

        db.statement().qualifyColumnNames(false).doNotCache() +
            "CREATE INDEX " + Users + "_name on " + Users + "(" + Users.name + ")" run {}

        var reps = 0

        var stPetersburgId = 0
        var munichId = 0
        var pragueId = 0
        db.transaction(maxRetries=2) {
            Cities.apply {
                stPetersburgId = db.insertInto(Cities) { row ->
                    row[name] = "St. Petersburg"
                } run { r -> r[id] }

                println("St. Petersburg is id $stPetersburgId")

                munichId = db.insertInto(this)  { row ->
                    row.setFromLambda(name) { i: Int, stmt: PreparedStatement -> stmt.setString(i, "Munich") }
                } run { r -> r[id] }
                println("Munich id $munichId")
            }

            reps++
            if (reps < 2) {
                throw RuntimeException("Transaction test")
            }
            pragueId = Cities.run {
                db.insertInto(this)  { row ->
                    row[name] = "Prague"
                } run { r -> r[id] }
            }
            println("Prague id $pragueId")

            db.insertInto(Cities) run { row ->
                row[Cities.name] = "Lonely"
            }
        }
        println()

        Cities.apply {
            db.select(columns).from(this)  run { query ->
                while (query.next()) {
                    val idVal : Int = query[id]
                    print("\tid: $idVal")
                    // We can do it manually, too.
                    val n: String = query.getFromLambda(name) { r, i -> r.getString(i) }
                    println("\tname:  $n")
                }
            }
        }
        println();

        {
            val idArg = Parameter(Types.sqlInt)
            Cities.apply {
                db.select(name).from(this) + " WHERE id > " + idArg run { query ->
                    query[idArg] = 4
                    while (query.next()) {
                        println("\t${query[name]}")
                    }
                }
            }
            println()

            val pragueName = db.select(Cities.name).from(Cities) + "WHERE id = " + idArg run { query ->
                query[idArg] = pragueId
                query.nextOrFail()
                query[Cities.name]
            }
            println("""    Prague is called "$pragueName"""")
            val munichName = db.select(Cities.name).from(Cities) + "WHERE id = " + idArg run { query ->
                query[idArg] = munichId
                query.nextOrFail()
                query[Cities.name]
            }
            println("""    Munich is called "$munichName"""")  // Recycle same statement
            println()
        }()

        Users.apply {
            db.insertInto(this) run { row ->
                row[id] = "andrey"
                row[name] = "Andrey"
                row[cityId] = stPetersburgId
            }

            db.insertInto(this) run { row ->
                row[id] = "sergey"
                row[name] = "Sergey"
                row[cityId] = munichId
            }

            db.insertInto(this) run { row ->
                row[id] = "eugene"
                row[name] = "Eugene"
                row[cityId] = munichId
            }

            db.insertInto(this) run { row ->
                row[id] = "alex"
                row[name] = "Alex"
                row[cityId] = null
            }

            db.insertInto(this) run { row ->
                row[id] = "smth"
                row[name] = "Something"
                // cityId defaults to null
            }
            db.select(columns).from(this) run  { r ->
                while (r.next()) {
                    println("\t${r[id]}\t${r[name]}\t${r[cityId]}")
                }
            }
            println()
        }

        CitiesCount.apply {
            db.select(columns).from(table) run { s ->
                s.nextOrFail()
                println("${s[count]} cities, maxId is ${s[maxId]}")
            }
        }

        val num = Users.run {
            val idStr = Parameter(Types.sqlString)
            db.update(this) + "WHERE " + id + "=" + idStr run { u ->
                u[idStr] = "alex"   // Sets the parameter
                u[name] = "Alexy"   // Sets the new value of the name column
                u[cityId] = pragueId    // Sets the new value of the cityId column
            }
        }
        println("Updated $num row");

        {
            val idParam = Parameter(Types.sqlString)
            val deleted = db.deleteFrom(Users) + "WHERE (" +
                Users.name + " LIKE '%thing') " + " AND (" +
                Users.id + " <> " + idParam + ")" run
                { d ->
                    d[idParam] = "nobody"
                }
            println("$deleted row deleted")
        }()

        db.select(Users.name, Cities.name).from(Users) +
                " INNER JOIN "  + Cities + " ON " + Cities.id + "=" + Users.cityId +
                " ORDER BY " + Users.name run
        { r ->
            while (r.next()) {
                println("\t${r[Users.name]}\t${r[Cities.name]}")
            }

        }
        println()

        db.select(Users.name).from(Users).doNotCache() run { r ->
            while (r.next()) {
                print("\t${r[Users.name]}")
            }
            println()
        }
        println()

        db.select(Users.columns).from(Users).forUpdate() run { r ->
            while (r.next()) {
                if (r[Users.id] == "andrey") {
                    r.delete()
                } else {
                    r[Users.name] = r[Users.name] + " V. Modified"
                    r.update()
                }
            }
            r.insert {
                r[Users.id] = "new"
                r[Users.name] = "New Girl"
            }
        }

        db.select(Users.name).from(Users).doNotCache() run { r ->
            while (r.next()) {
                print("\t${r[Users.name]}")
            }
            println()
        }
        println()

        db.insertInto(Users) run { row ->
            val json = mapOf("Users.id" to "json", "Users.name" to "Jason", "Users.city_id" to null)
            row.setFromJson(json)
        }
        db.insertInto(Users) run { row ->
            val json = mapOf("Users.id" to "json2", "Users.name" to "Jason 2", "Users.city_id" to pragueId)
            row.setFromJson(json)
        }
        // We can use the variant with a results body, too.  Since city_id is nullable, we don't
        // need to pass a value for it:
        db.insertInto(Users) { row ->
            val json = mapOf("Users.id" to "json3", "Users.name" to "Jason 3")
            row.setFromJson(json)
        } run {}

        {
            val userId = Parameter(Types.sqlString)
            db.update(Users) + "WHERE " + Users.id + "=" + userId run { u ->
                u[userId] = "json2"
                val json = mapOf("Users.name" to "Jason II")
                u.setFromJson(json)
            }
            db.select(Users.columns).from(Users).forUpdate() +
                    "WHERE " + Users.id + "=" + userId run { s ->
                s[userId] = "json3"
                s.nextOrFail()
                val json = mapOf("Users.name" to "Jason III")
                s.setFromJson(json)
                s.update()
                s.insertFromJson(mapOf("Users.id" to "json4", "Users.name" to "Jason IV"))
            }
        }()

        db.select(Users.columns).from(Users) run { r ->
            while (r.next()) {
                println("${r.getJson()}")
            }
        }
        println()

        db.dropTable(Users, ifExists=true)
        db.dropTable(Cities)
    }
    println()
    println()
}
