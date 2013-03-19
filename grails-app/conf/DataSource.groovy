hibernate {
    cache.use_second_level_cache = false
    cache.use_query_cache = false
}
// environment specific settings
environments {
    test {
        dataSource {
            driverClassName = "org.h2.Driver"
            dialect = "org.hibernate.dialect.H2Dialect"
            username = "sa"
            password = ""
            dbCreate = "create-drop"
            url = "jdbc:h2:mem:devDb;MVCC=TRUE"

        }
    }
    development {
        dataSource {         
            loggingSql = true
            formatSql = true
            driverClassName = "org.h2.Driver"
            dialect = "org.hibernate.dialect.H2Dialect"
            username = "sa"
            password = ""
            dbCreate = "create-drop"
            url = "jdbc:h2:mem:devDb;MVCC=TRUE"

        }
    }
}

/*
 *
DELETE from DROOLS_TEST WHERE 1;
DELETE from SESSION_INFO WHERE 1;
 */