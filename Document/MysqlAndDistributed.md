### Mysql与分布式
前面我们讲解了Redis在分布式场景下的相关应用 接着我们来看看Mysql数据库在分布式场景下的应用

### 主从复制
当我们使用Mysql的时候 也可以采取主从复制的策略 它的实现思路基本和Redis相似 也是采用增加量复制的方式 Mysql会在运行的过程中 会记录二进制日志 所有的DML和DDL操作都会被记录进日志中
主库只需要将记录的操作复制给从库 让从库也运行一次 那么就可以实现主从复制 但是注意它不会在一开始进行全量复制 所以最好再开始主从之前将数据库的内容保持一致

和之前一样 一旦我们实现了主从复制 那么就算主库出现故障 从库也能正常提供服务 并且还可以实现读写分离等操作
这里我们就使用两台主机来搭建一主一从的环境 首先确保两台服务器都安装了Mysql数据库并且都已经正常运行了:

<img src="https://fast.itbaima.net/2023/03/07/95wL8vICYNp61T2.jpg"/>

接着我们需要创建对应的账号 一会方便从库进行访问的用户:

```mysql
                    CREATE USER test identified with mysql_native_password by '123456';
```

接着我们开启一下外网访问:

```shell
                    sudo vim /etc/mysql/myslq.conf.d/mysqld.cnf
```

修改配置文件:

```editorconfig
                    # If MySQL is running as a replication slave, this should be
                    # changed. Ref https://dev.mysql.com/doc/refman/8.0/en/server-system-variables.html#sysvar_tmpdir
                    # tmpdir                = /tmp
                    #
                    # Instead of skip-networking the default is now to listen only on
                    # localhost which is more compatible and is not less secure.
                    # bind-address          = 127.0.0.1    这里注释掉就行
```

现在我们重启一下Mysql服务:

```shell
                    sudo systemctl restart mysql.service
```

现在我们首先来配置主库 主库只需要为我们刚刚创建好的用户分配一个主从复制的权限即可:

```mysql
                    grant replication slave on *.* to test;
                    FLUSH PRIVILEGES;
```

然后我们可以输入命令来查看主库的相关情况:

<img src="https://fast.itbaima.net/2023/03/07/kqHZoc8xAbNOd3K.jpg"/>

这样主库就搭建完成了 接着我们需要将从库进行配置 首先是配置文件:

```editorconfig
                    # The following can be used as easy to replay backup logs or for replication.
                    # note: if you are setting up a replication slave, see README.Debian about
                    #       other settings you may need to change.
                    # 这里需要将server-id配置为其他的值（默认是1）所有Mysql主从实例的id必须唯一 不能打架 不然一会开启会失败
                    server-id               = 2
```

进入数据库 输入:

```mysql
                    change replication source to SOURCE_HOST='192.168.0.8',SOURCE_USER='test',SOURCE_PASSWORD='123456',SOURCE_LOG_FILE='binlog.000004',SOURCE_LOG_POS=591;
```

注意后面的logfile和pos就是我们上面从主库中显示的信息

<img src="https://fast.itbaima.net/2023/03/07/H7BIl9s3kPu2Mnw.jpg"/>

执行完成后 显示OK表示没有问题 接着输入:

```mysql
                    start replica;
```

现在我们的从机就正式启动了 现在我们输入:

```mysql
                    show replica status\G;
```

来查看当前从机状态 可以看到:

<img src="https://fast.itbaima.net/2023/03/07/KiCoVP1cGaf94uX.jpg"/>

最关键的是下面的Replica_IO_Running和Replica_SQL_Running必须同时为Yes才可以 实际上从库会创建两个线程 一个线程负责与主库进行通信
获取二进制日志 暂时存放到一个中间表(Relay_Log)中 而另一个线程则是将中间表保存的二进制日志的信息进行执行 然后插入到从库中:

最后配置完成 我们来看看在主库进行操作会不会同步到从库:

<img src="https://fast.itbaima.net/2023/03/07/RxNB3QmUYESX5ad.jpg"/>

可以看到在主库中创建的数据库 被同步到从库中了 我们再来试试看创建表和插入数据:

```mysql
                    use yyds;
                    create table test (
                        `id` int primary key,
                        `name` varchar(255) NULL,
                        `passwd` varchar(255) NULL
                    );
```

<img src="https://fast.itbaima.net/2023/03/07/qKBwz31P6ySxlZt.jpg"/>

现在我们随便插入一点数据:

<img src="https://fast.itbaima.net/2023/03/07/9pqBFXiLhTPc2xO.jpg"/>

这样 我们的Mysql主从就搭建完成了 那么如果主机此时挂了会怎么样?

<img src="https://fast.itbaima.net/2023/03/07/s1Q5xt32r6dv9UJ.jpg"/>

可以看到IO线程是处于重连状态 会等待主库重新恢复运行

### 分库分表
在大型的互联网系统中 可能单台Mysql的存储容量无法满足业务的需求 这时候就需要进行扩容了

和之前的问题一样 单台主机的硬件资源存在瓶颈的 不可能无限制地纵向扩展 这时我们就得通过多台实例来进行容量的横向扩容 我们可以将数据分散存储 让多台主机共同来保存数据

那么问题来了 怎么个分散法?

- **垂直拆分**: 我们的表和数据库都可以进行垂直拆分 所谓垂直拆分 就是将数据库中所有的表 按照业务功能拆分到各个数据库中(是不是感觉跟前面两章的学习的架构对应起来了) 而对于一张表 也可以通过外键之类的机制 将其拆分多个表

    <img src="https://fast.itbaima.net/2023/03/07/mnJO4hBwDAkRcMi.jpg"/>

- **水平拆分**: 水平拆分针对的不是表 而是数据 我们可以让多个具有相同表的数据库存放一部分数据 相当于是将数据分散到存储在各个节点上

    <img src="https://fast.itbaima.net/2023/03/07/AdS5hrH2O1l8iqv.jpg"/>

那么要实现这样的拆分操作 我们自行去编写代码工作量肯定是比较大的 因此目前实际上已经有一些解决方案了 比如我们可以使用MyCat(也是一个数据库中间件 相当于挂了一层代理 再通过MyCat进行分库分表操作数据库
只需要连接就能使用 类似的还有ShardingSphere-Proxy) 或是Sharding JDBC(应用程序中直接对SQL语句进行分析 然后转换成分库分表操作 需要我们自己编写一些逻辑代码) 再这里我们就讲解一下Sharding JDBC

### Sharding JDBC
<img src="https://fast.itbaima.net/2023/03/07/HTlcExgCfZvG9MP.jpg"/>

官方文档(中文): https://shardingsphere.apache.org/document/5.1.0/cn/overview/#shardingsphere-jdbc

定位为轻量级Java框架 在Java的JDBC层提供的额外服务 它使用客户端直连数据库 以jar包形式提供服务 无需额外部署和依赖 可理解为增强版的JDBC驱动 完全兼容JDBC和各种ORM框架
- 适用于任何基于JDBC的ORM框架 如: JPA, Hibernate, Mybatis, Spring JDBC Template或直接用JDBC
- 支持任何第三方的数据库连接池 如: DBCP, C3P0, BoneCP, HikariCP等
- 支持任意实现JDBC规范的数据库 目前支持MySQL, PostgerSQL Oracle, SQLServer以及任何可使用JDBC访问的数据库

这里我们主要演示一下水平分表方式 我们直接创建一个新的SpringBoot项目即可 依赖如下:

```xml
                    <dependencies>
                          <!-- ShardingJDBC依赖 那必须安排最新版啊 希望你们看的时候还是5.X版本 -->
                        <dependency>
                            <groupId>org.apache.shardingsphere</groupId>
                            <artifactId>shardingsphere-jdbc-core-spring-boot-starter</artifactId>
                            <version>5.1.0</version>
                        </dependency>
                      
                        <dependency>
                            <groupId>org.mybatis.spring.boot</groupId>
                            <artifactId>mybatis-spring-boot-starter</artifactId>
                            <version>2.2.2</version>
                        </dependency>
                    
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <optional>true</optional>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
```

数据库我们这里直接用上一章的即可 因为只需要两个表结构一样的数据库即可 正好上一章进行了同步 所以我们直接把从库变回正常状态就可以了:

```mysql
                    stop replica;
```

接着我们把两个表的root用户密码改一下 一会用这个用户连接数据库:

```mysql
                   update user set authentication_strin = '' where user = 'root';
                   update user set host = '%' where user = 'root';
                   alter user root identified with mysql_native_password by '123456';
                   FLUSH PRIVILEGES;
```

接着我们来看 如果直接尝试开启服务器 那肯定是开不了的 因为我们要配置数据源:

<img src=""/>















