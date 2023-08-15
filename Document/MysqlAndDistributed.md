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

<img src="https://s2.loli.net/2023/03/07/GEfPLSIZyobhtTe.jpg"/>

那么数据源该怎么配置呢? 现在我们是一个分库分表的状态 需要配置两个数据源:

```yaml
                    spring
                      shardingsphere:
                        datasource:
                          # 有几个数据就配置几个 这里是名称 按照下面的格式 名称+数字的形式
                          names: db0,db1
                          # 为每个数据源单独进行配置
                          db0:
                            # 数据源实现类 这里使用默认的HikariDataSource
                            type: com.zaxxer.hikari.HikariDataSource
                            # 数据库驱动
                            dirver-class-name: com.mysql.cj.jdbc.Driver
                            # 不用我多说了吧
                            jdbc-url: jdbc:mysql://192.168.0.8:3306/yyds
                            username: root
                            password: 123456
                          db1:
                            type: com.zaxxer.hikari.HikariDataSource
                            dirver-class-name: com.mysql.cj.jdbc.Driver
                            jdbc-url: jdbc:mysql://192.168.0.13:3306/yyds
                            username: root
                            password: 123456
```

如果启动没有问题 那么就是配置成功了:

<img src="https://s2.loli.net/2023/03/07/Hvm82dfbEtwBqrA.jpg"/>

接着我们需要对项目进行一些编写 添加我们的用户实体类和Mapper:

```java
                    @Data
                    @AllArgsCOnstructor
                    public class User {
                        
                        Integer id;
                        String name;
                        String passwd;
    
                    }
```
```java
                    @Mapper
                    public interface UserMapper {
    
                        @Select("select * from test where id = #{id}")
                        User getUserById(Integer id);                      
                        
                        @Insert("insert into test(id,name,passwd) values(#{id},#{name},#{passwd})")
                        int addUser(User user);
  
                    }           
```

实际上这些操作都是常规操作 在编写代码时关注点依然放在业务本身上 现在我们就来编写配置文件 我们需要告诉ShardingJDBC要如何进行分片
首先明确: 现在是两个数据库都有test表存放用户数据 我们目标是将用户信息分别存放到这两个数据库的表中

不废话了 直接上配置:

```yaml
                    spring:
                      sharding:
                        tables:
                          # 这里填写表名称 程序中对这张表的所有操作 都会采用下面的路由方案
                          # 比如我们上面Mybatis就是对test表进行操作 所以会走下面的路由方案
                          test:
                            # 这里填写实际的路由节点 比如现在我们要分两个库 那么就可以把两个库都写上 以及对应的表
                            # 也可以使用表达式 比如下面的可以简写为 db$->{0..1}.test
                            actual-data-nodes: db0.test,db1.test
                            # 这里是分库策略配置
                            database-strategy:
                              # 这里选择标准策略 也可以配置复杂策略 基于多个键进行分片
                              standard:
                                # 参与分片运算的字段 下面的算法会根据这里提供的字段进行运算
                                sharding-column: id
                                # 这里填写我们下面自定义的算法名称
                                sharding-algorithm-name: my-alg  
                        sharding-algorithms:
                          # 自定义一个新的算法 名称随意
                          my-alg:
                            # 算法类型 官方内置了很多种 这里演示最简单的一种
                            type: MOD
                            props:
                              sharding-count: 2
                            
                      props:
                        # 开启日志 一会方便我们观察
                        sql-show: true

```

其中 分片算法有很多内置的 可以在这里查询: https://shardingsphere.apache.org/document/5.1.0/cn/user-manual/shardingsphere-jdbc/builtin-algorithm/sharding/
这里我们使用的是MOD 也就是取模分片算法 它会根据主要键的值进行取模运算 比如我们这里填写的是2 那么就表示对主键进行模2运算 根据数据源的名称 比如db0就是取模后为0 db1就是取模后为1
(官方文档描述的并不是很清楚) 也就是说 最终实现的效果就是单数放在db1 双数放在db0 当然它还支持一些其他的算法 这里就不多介绍了

那么现在我们编写一个测试用例来看看 是否能够按照我们上面的规则进行路由:

```java
                    @SpringBootTest
                    class ShardingJdbcTestApplciationTests {
                        
                        @Resource
                        private UserMapper mapper;                      
  
                        @Test
                        void contextLoads() {
                            for(int i = 0; i < 10; i++) {
                               // 这里ID自动生成1-9 然后插入数据库
                               mapper.addUser(new User(i, "xxx", "ccc")); 
                            }
                        }
  
                    }
```

现在我们可以开始运行了:

<img src="https://s2.loli.net/2023/03/07/7oBrFRwiXQxcumz.jpg"/>

测试通过 我们来看看数据库里面是不是按照我们的规则进行数据插入的:

<img src="https://s2.loli.net/2023/03/07/kZINi9wmnte3J7g.jpg"/>

可以看到这两张表 都成功按照我们指定的路由规则进行插入了 我们来看看详细的路由情况 通过控制台输出的SQL就可以看到:

<img src="https://img-blog.csdnimg.cn/img_convert/2e9cd91031d3fc7d2f11a2f59d8841ae.png"/>

可以看到所有的SQL语句都有一个Logic SQL (这个就是我们在Mybatis里面写的 是上面就是什么) 紧接着下面就是Actual SQL
也就是说每个逻辑SQL最终会根据我们的策略转换为实际SQL 比如第一条数据 它的id是0 那么实际转换出来的SQL会在db0这个数据源进行插入

这样我们就很轻松地实现了分库策略

分库完成之后 接着我们来看分表 比如现在我们的数据库中有test_0和test_1两张表 表结构一样 但是我们也是希望能够根据id取模运算的结果分别放到这两个不同的表中 实现思路其实是差不多的 这里首先需要介绍一下两种表概念:
- 逻辑表: 相同结构的水平拆分数据库(表)的逻辑名称 是SQL中表的逻辑标识 例: 订单数据根据主键尾数拆分为10张表 分别是t_order_0到t_order_9 它们的逻辑表名为t_order
- 真实表: 在水平拆分的数据库中真实存在的物理表 即上个示例中的t_order_0到t_order_9

现在我们就以一号数据库为例 那么我们在里面创建上面提到的两张表 之前的那个test表删不删都可以 就当做不存在就行了:

```mysql
                    create table test_0 (
                      `id` int primary key,
                      `name` varchar(255) NULL,
                      `passwd` varchar(255) NULL
                    );

                    create table test_1 (
                      `id` int primary key,
                      `name` varchar(255) NULL,
                      `passwd` varchar(255) NULL
                    );
```

<img src="https://s2.loli.net/2023/03/07/InHsNXA3E8dQBPa.jpg"/>

接着我们不要去修改任何的业务代码 Mybatis里面写的是什么依然保持原样 即使我们的表名已经变了 我们需要做的是通过路由来修改原有的SQL 配置如下:

```yaml
                    spring:
                    shardingsphere:
                      rules:
                        sharding:
                          tables:
                            test:
                              actual-data-nodes: db0.test_$->{0..1}
                              # 现在我们来配置一下分表策略 注意这里是table-strategy上面是database-strategy
                              table-strategy:
                                # 基本都跟之前是一样的
                                standard:
                                  sharding-column: id
                                  sharding-algorithm-name: my-alg
                          sharding-algorithms:
                            my-alg:
                              # 这里我们演示一下INLINE方式 我们可以自行编写表达式来决定
                              type: INLINE
                              props:
                                # 比如我们还是希望进行模2计算得到数据该去的表
                                # 只需要给一个最终的表名称就行了test_ 后面的数字是表达式取模算出的
                                # 实际上这样写和MOD模式一模一样
                                algorithm-expression: test_$->{id % 2}
                                # 没错 查询也会根据分片策略来进行 但是如果我们使用的是范围查询 那么依然会进行全量查询
                                # 这个我们后面紧接着会讲 这里先写上吧
                                allow-range-query-with-inline-sharding: false
```

现在我们来测试一下 看看会不会按照我们的策略进行分表插入:

<img src="https://s2.loli.net/2023/03/07/OaRCMTJ1lnIicSd.jpg"/>

可以看到 根据我们的算法 原本的逻辑表被修改为了最终进行分表计算后的结果 我们来查看一下数据库:

<img src="https://s2.loli.net/2023/03/07/lfvgOjanPZHMNdr.jpg"/>

插入我们了解完毕了 我们来看看查询呢:

```java
                    @SpringBootTest
                    class ShardingJdbcTestApplicationTests {
                    
                        @Resource
                        private UserMapper mapper;
                        
                        @Test
                        void contextLoads() {
                            System.out.println(mapper.getUserById(0));
                            System.out.println(mapper.getUserById(1));
                        }
                        
                    }
```

<img src="https://s2.loli.net/2023/03/07/7K1WBk3s8HuMeOI.jpg"/>

可以看到 根据我们配置的策略 查询也会自动选择对应的表进行 是不是感觉有内味了

那么如果是范围查询呢?

```java
                    @Select("select * from where id between #{start} and #{end}")
                    List<User> getUsersByIdRange(int start, int end);
```
```java
                    @SpringBootTest
                    class ShardingJdbcTestApplicationTests {
                        
                        @Resource
                        private UserMapper mapper;                      
                        
                        @Test
                        void contextLoads() {
                            System.out.println(mapper.getUsersByIdRange(3, 5));
                        }
                        
                    }
```

我们来看看执行结果会怎么样:

<img src="https://s2.loli.net/2023/03/07/3Hj7s4xqEwiFXJB.jpg"/>

可以看到INLINE算法默认是不支持进行全量查询的 我们得将上面的配置项改成true:

```yaml
                    allow-range-query-with-inline-sharding: true
```

再次进行测试:

<img src="https://s2.loli.net/2023/03/07/WoQqNLCXJslBT3D.jpg"/>

可以看到 最终出来的SQL语句是直接对两个表都进行查询 然后求出一个并集出来作为最后的结果 当然除了分片之外 还有广播表和绑定表机制 用于多种业务场景下 这里就不多做介绍了 详细请查阅官方文档

### 分布式序列算法
前面我们讲解了如何进行分库分表 接着我们来看看分布式序列算法

在复杂分布式系统中 特别是微服务架构中 往往需要对大量的数据和消息进行唯一标识 随着系统的复杂 数据的增多 分库分表成为了常见的方案
对数据分库分表后需要一个唯一ID来标识一条数据或消息(如订单号, 交易流水, 事件编号等) 此时一个能够生成全局唯一ID的系统是非常必要的

比如我们之前创建过学生信息表 图书借阅表 图书管理表 所有的信息都有一个ID作为主要键 并且这个ID有以下要求:
- 为了区别于其他的数据 这个ID必须是全局唯一的
- 主键应该尽可能保持有序 这样会大大提升索引的查询效率

那么我们在分布式系统下 如何保证ID的生成满足上面的需求呢?

1. `使用UUID`: UUID是由一组32位数的16进制数字随机构成的 我们可以直接使用JDK为我们提供的UUID类来创建:

    ```java
                        public static void main(String[] args) {
                            String uuid = UUID.randomUUID().toString();
                            System.out.println(uuid);
                        }
    ```
   
   结果为34ad0796-8ee6-4462-8726-3d17aea62490 生成速度非常快 可以看到确实是能够保证唯一性 因为每次都不一样 而且这么长一串那重复的概览真的是小的可怜
       
   但是它并不满足我们上面的第二个要求 也就是说我们需要尽可能的保证有序 而这里我们得到的都是一些无序的ID


2. `雪花算法(Snowflake)`: 我们来看雪花算法 它会生成一个64bit大小的整型的ID int肯定是装不下了

    <img src="https://s2.loli.net/2023/03/07/lU9A4zjSIKvaxwh.jpg"/>

   可以看到它主要是三个部分组成 时间+工作机器ID+序列号 时间以毫秒为单位 41个bit位能表示约70年的时间 时间纪元从2016年11月1日零点开始 可以使用到2086年 工作机器ID其实就是节点ID
   每个节点的ID都不相同 那么就可以区分出来 10个bit位可以表示最多1024个节点 最后12位就是每个节点下的序列号 因此每台机器每毫秒就可以有4096个系列号

   这样 它就兼具了上面所说的唯一性和有序性了 但是依然是有缺点的 第一个是时间问题 如果机器时间出现倒退 那么就会导致生成重复的ID 并且节点容量只有1024个 如果是超大规模集群 也是存在隐患的

ShardingJDBC支持以上两种算法为我们自动生成ID 文档: https://shardingsphere.apache.org/document/5.1.0/cn/user-manual/shardingsphere-jdbc/builtin-algorithm/keygen/

这里 我们就是要ShardingJDBC来让我们的主键ID以雪花算法进行生成 首先是配置数据库 因为我们默认的id是int类型 装不下64位的 改一下:

```mysql
                    ALTER TABLE `yyds`.`test` MODIFY COLUMN `id` bigint NOT NULL FIRST;
```

接着我们需要修改一下Mybatis的插入语句 因为现在id是由ShardingJDBC自动生成 我们就不需要自己加了:

```java
                    @Insert("insert into test(name,passwd) values(#{name},#{passwd})")
                    int addUser(User user);
```

接着我们在配置文件中将我们的算法写上:

```yaml
                    spring:
                    shardingsphere:
                      datasource:
                        sharding:
                          tables:
                            test:
                              actual-data-nodes: db0.test,db1.test
                              # 这里还是使用分库策略
                              database-strategy:
                                standard:
                                  sharding-column: id
                                  sharding-algorithm-name: my-alg
                              # 这里使用自定义的主键生成策略
                              key-generate-strategy:
                                column: id
                                key-generator-name: my-gen
                          key-generators:
                            # 这里写我们自定义的主键生成算法
                            my-gen:
                              # 使用雪花算法
                              type: SNOWFLAKE
                              props:
                                # 工作机器ID 保证唯一就行
                                worker-id: 666
                          sharding-algorithms:
                            my-alg:
                              type: MOD
                              props:
                                sharding-count: 2
```

接着我们来编写一下测试用例:

```java
                    @SpringBootTest
                    class ShardingJdbcTestApplicationTests {
                        
                        @Resource
                        private UserMapper mapper;
                        
                        @Test
                        void contextLoads() {
                            
                            for (int i = 0; i < 20; i++) {
                                mapper.addUser(new User("aaa", "bbb"));
                            }
                            
                        }
                        
                    }
```

可以看到日志:

<img src="https://s2.loli.net/2023/03/07/2JBaqnV8k9OWYfw.jpg"/>

在插入的时候 将我们的SQL语句自行添加了一个id字段 并且使用的是雪花算法生成的值 并且也是根据我们的分库策略在进行插入操作

### 读写分离
最后我们来看看读写分离 我们之前实现了Mysql的主从 那么我们就可以将主库作为读 从库作为写:

<img src="https://s2.loli.net/2023/03/07/KRBbGXxhkmUHFIr.jpg"/>

这里我们还是将数据库变回主从状态 直接删除当前的表 我们重新来过:

```mysql
                    drop table test;
```

我们需要将从库开启只读模式 在Mysql配置中进行修改:

```editorconfig
                    read-only    = 1
```

这样从库就只能读数据了(但是root账号还是可以写数据) 接着我们重启服务器:

```shell
                    sudo systemctl restart mysql.service
```

然后进入主库 看看状态:

<img src="https://s2.loli.net/2023/03/07/8o4YIB5MysaUuFx.jpg"/>

现在我们配置一下从库:

```mysql
                    change replication source to SOURCE_HOST='192.168.0.13',SOURCE_USER='test',SOURCE_PASSWORD='123456',SOURCE_LOG_FILE='binlog.000007',SOURCE_LOG_POS=19845;
```

现在我们在主库创建表:

```mysql
                    create table test (
                        `id` bigint primary key,
                        `name` varchar(255) NULL,
                        `passwd` varchar(255) NULL
                    )
```

然后我们就可以配置ShardingJDBC了 打开配置文件:

```yaml
                    spring:
                    shardingsphere:
                      rules:
                        # 配置读写分离
                        readwrite-splitting:
                          data-sources:
                            # 名称随便写
                            user-db:
                              # 使用静态类型 动态Dynamic类型可以自动发现auto-aware-data-source-name 这里不演示
                              type: Static
                              props:
                                # 配置写库(只能一个)
                                write-data-source-name: db0
                                # 配置从库(多个 逗号隔开)
                                read-data-source-names: db1
                                # 负载均衡策略 可以自定义
                                load-balancer-name: my-load
                          load-balancers:
                            # 自定义的负载均衡策略
                            my-load:
                              type: ROUND_ROBIN
```

注意把之前改的用户实体类和Mapper改回去 这里我们就不用自动生成ID的了 所有的负载均衡算法地址: https://shardingsphere.apache.org/document/5.1.0/cn/user-manual/shardingsphere-jdbc/builtin-algorithm/load-balance/

现在我们就来测试一下吧:

```java
                    @SpringBootTest
                    class ShardingJdbcTestApplicationTests {
                    
                        @Resource
                        UserMapper mapper;
                    
                        @Test
                        void contextLoads() {
                            
                            mapper.addUser(new User(10, "aaa", "bbb"));
                            System.out.println(mapper.getUserById(10));
                            
                        }
                    
                    }
```

运行看看SQL日志:

<img src="https://s2.loli.net/2023/03/07/zJvqKmfyhVMFLtZ.jpg"/>

可以看到 当我们执行插入操作时 会直接向db0进行操作 而读取操作是会根据我们的配置 选择db1进行操作

至此 微服务应用章节到此结束