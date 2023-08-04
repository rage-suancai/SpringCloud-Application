### Redis与分布式
在SpringBoot阶段 我们学习了Redis 它是一个基于内存的高性能数据库 我们当时已经学习了包括基本操作, 常用数据类型, 持久化, 事务和锁机制以及使用Java与Redis进行交互等
利用它的高性能 我们还使用它来做Mybatis的二级缓存, 以及Token的持久化存储 而这一部分 我们将继续深入 探讨Redis在分布式开发场景下的应用

### 主从复制
在分布式场景下 我们可以考虑让Redis实现主要从模式:

<img src="https://fast.itbaima.net/2023/03/07/bzwPflgBD5O1saN.png"/>

主从复制 是指将一台Redis服务器的数据 复制到其他的Redis服务器 前者称为主节点(Master)
后者称为从节点(Slave) 数据的复制是单向的只能由主节点到从节点 Master以写为主 Slave以读为主

这样的好处肯定是显而易见的:
- 实现了读写分离 提高了性能
- 在写少读多的场景下 我们甚至可以安排很多个从节点 

那么我们现在就来尝试实现一下 这里我们还是在Windows下进行测试 打开Redis文件夹 我们要开启两个Redis服务器 修改配置文件redis.windows.conf:

```editorconfig
                    # Accept connections on the specified port, default is 6379 (IANA #815344).
                    # If port 0 is specified Redis will not listen on a TCP socket.
                    port 6001
```

一个服务器的端口设定为6001 复制一份 另一个的端口为6002 接着我们指定配置文件进行启动 打开cmd:

<img src="https://fast.itbaima.net/2023/03/07/Si54lok9eqtKPf1.png"/>

现在我们的两个服务器就启动成功了 接着我们可以使用命令查看当前服务器的主从状态 我们打开客户端:

<img src="https://fast.itbaima.net/2023/03/07/2TbMQeZknSOzFpy.png"/>

输入info replication命令来查看当前的主从状态 可以看到默认的角色为: master 也就是说所有的服务器在启动之后都是主节点的状态 那么现在我们希望让6002作为从节点 通过一个命令即可:

<img src="https://fast.itbaima.net/2023/03/07/XqpNcihJ5jsZRoI.png"/>

可以看到 在输入replication 127.0.0.1 6001命令后 就会将6001服务器作为主节点 而当前节点作为6001的从节点 并且角色也会变成: slave 接着我们来看看6001的情况:

<img src="https://fast.itbaima.net/2023/03/07/YABKJDsbQkE1UM5.png"/>

可以看到从节点信息中已经出现了6002服务器 也就是说现在我们的6001和6002就形成了主从关系(还包含了一个偏移量 这个偏移量反应的是从节点的同步情况)

    主服务器和从服务器都会维护一个复制偏移量 主服务器每次向从服务器中传递N个字节的时候 会将自己的复制偏移量加上N 从服务器中收到主服务器的N个字节的数据
    就会将自己额复制偏移量加上N 通过主要从服务器的偏移量对比可以很清楚的知道主从服务器的数据是否处于一致 如果不一致就需要进行增量同步了

那么我们现在可以来测试一下 在主要节点新增数据 看看是否会同步到从节点:

<img src="https://fast.itbaima.net/2023/03/07/taxoisA8Tpg2DWM.png"/>

可以看到 我们在6001服务器插入的a 可以在从节点6002读取到 那么从节点新增的数据在主节点能得到吗? 我们来测试一下:

<img src="https://fast.itbaima.net/2023/03/07/dS2V8xafPj6lKND.png"/>

可以看到 从节点压根就没办法进行数据插入 节点的模式为只读模式 那么如果我们现在不想让6002作为6001的从节点了呢?

<img src="https://fast.itbaima.net/2023/03/07/dV7Rxov6pblW2g5.png"/>

可以看到 通过输入replicaof on one 即可变回Master角色 接着我们再来启动一台6003服务器 流程是一样的:

<img src="https://fast.itbaima.net/2023/03/07/TC7z2mt3EGMPWfq.png"/>

可以看到 在连接之后 也会直接同步主节点的数据 因此无论是已经处于从节点状态还是刚刚启动完成的服务器 都会从主节点同步数据 实际上整同步流程为:

1. 从节点执行replicaof ip port命令后 从节点会保存主节点相关的地址信息
2. 从节点通过每秒运行的定时任务发现配置了新的主节点后 会尝试与该节点建立网络连接 专门用于接收到主节点发送的复制命令
3. 连接成功后 第一次会将主节点的数据进行全量复制 之后采用增量复制 持续将新来的写命令同步给从节点

当我们的主节点关闭后 从节点依然可以读取数据:

<img src="https://fast.itbaima.net/2023/03/07/MmNshyQxa2ijSRT.png"/>

但是从节点会疯狂报错:

<img src="https://fast.itbaima.net/2023/03/07/pEIo93MQXShrsZD.png"/>

当然每次都去敲个命令配置主从太麻烦了 我们可以直接在配置文件中配置 添加这样行即可:

```editorconfig
                    replicaof 127.0.0.1 6001
```

这里我们给6002和6003服务器都配置一下 现在我们重启三个服务器

<img src="https://fast.itbaima.net/2023/03/07/GpAa5kfyC3zVRZK.png"/>

当然 除了作为Master节点的从节点外 我们还可以将其作为从节点的从节点 比如现在我们让6003作为6002的从节点:

<img src="https://fast.itbaima.net/2023/03/07/OdAs1weYgkDrQvf.png"/>

也就是说 现在差不多是这样的一个情况:

<img src="https://fast.itbaima.net/2023/03/07/2ADSR8LtpMhCFfK.png"/>

采用这种方式 优点肯定是显而易见的 但是缺点也很明显 整个传播链路一旦中途出现问题 那么就会导致后面的从节点无法及时同步

### 哨兵模式
前面我们讲解了Redis实现主从复制的一些基本操作 那么我们接着来看哨兵模式

经过之前的学习 我们发现 实际上最关键的还是主节点 因为一旦主节点出现问题 那么整个主从系统将无法写入 因此 我们得想一个办法 处理一下主节点故障的情况
实际上我们可以参考之前的服务治理模式 比如Nacos和Eureka 所有的服务都会被实时监控 那么主要出现问题 肯定是可以及时发现的 并且能够采取响应的补救措施 这就是我们即将介绍的哨兵:

<img src="https://fast.itbaima.net/2023/03/07/YGq8MDZbRK6E7Po.png"/>

注意这里的哨兵不是我们之前学习SpringCloud Alibaba的那个 是专用于Redis的 哨兵会对所有的节点进行监控 如果发现主节点出现问题 那么会立即让从节点进行投票
选举一个新的主节点出来 这样就不会由于主节点的故障导致整个系统不可写(注意: 要实现这样的功能最小的系统必须是一主一丛 再小的话就没有意义了)

<img src="https://fast.itbaima.net/2023/03/07/WhkUqfxcHn4CApP.png"/>

那么怎么启动一个哨兵呢? 我们只需要稍微修改一下配置文件即可 这里直接删除全部内容 添加:

```editorconfig
                    sentinel monitor yxsnb 127.0.0.1 6001 1
```

其中第一个和第二个是固定 第三个是为监控对象名称 随意 后面就是主节点的相关信息 包括IP地址和端口
最后一个1我们暂时先不说 然后我们使用此配置文件启动服务器 可以看到启动后:

<img src="https://fast.itbaima.net/2023/03/07/xB78t53RgykXvo9.png"/>

<img src="https://fast.itbaima.net/2023/03/07/STh2RgjW7ycPCNB.png"/>

可以看到以哨兵模式启动后 会自动监控主节点 然后还会显示那些节点是作为从节点存在的

现在我们直接把主要节点关闭 看看会发生什么事情:

<img src="https://fast.itbaima.net/2023/03/07/97HnwfuNjUK5qx4.png"/>

可以看到从节点还是正常的在报错 一开始的时候不会直接重新进行选举而是继续尝试重连(因为有可能只是网络小卡一下 没必要这么敏感)
但是我们发现 经过一段时间之后 依然无法连接 哨兵输出了以下内容:

<img src="https://fast.itbaima.net/2023/03/07/GWt8Q6mfSv7TgCb.png"/>

可以看到哨兵发现主节点已经有一段时间不可用了 那么就会开始进行重新选举 6003节点被选为了新的主节点 并且之前的主节点6001变成了新的主节点的从节点:

<img src="https://fast.itbaima.net/2023/03/07/4WzTVZ15dMiQ3f8.png"/>

<img src="https://fast.itbaima.net/2023/03/07/gGHVvOhBKe9wSEz.png"/>

当我们再次启动6001时 会发现 它自动变成了6003的从节点 并且会将数据同步过来:

<img src="https://fast.itbaima.net/2023/03/07/eqLycu8s1rSRtFa.png"/>

那么 这个选举规则是怎样的呢? 是在所有的从节点中随机选取还是遵循某种规则呢?
1. 首先会根据优先级进行选择 可以在配置文件中进行配置 添加replica-priority配置项(默认是100) 越小表示优先级越高
2. 如果优先级一样 那就选择偏移量最大的
3. 要是还选不出来 那就选择runid(启动时随机生成的)最小的

要是哨兵也挂了咋办? 没事 咱们可以多安排几个哨兵 只需要把哨兵的配置复制一下 然后修改端口 这样可以同时启动多个哨兵了 我们启动3个哨兵(一主二从三哨兵) 这里我们把最后一个值改为2:

```editorconfig
                    sentinel moitor yxsnb 192.168.0.8 6001 2
```

这个值实际上代表的是当有几个哨兵认为主节点关掉时 就判断主节点真的挂掉了

<img src="https://fast.itbaima.net/2023/03/07/48MNiLJXqmUtvWc.png"/>

现在我们把6001节点挂掉 看看这三个哨兵会怎么样:

<img src="https://fast.itbaima.net/2023/03/07/ajSAhqb5L9Yuorg.png"/>

可以看到都显示将master切换为6002节点了

那么 在哨兵重新选举新的主要节点之后 我们Java中的Redis客户端怎么感知到呢? 我们来看看 首先还是导入依赖:

```xml
                    <dependencies>
                        <dependency>
                            <groupId>redis.clients</groupId>
                            <artifactId>jedis</artifactId>
                            <version>4.2.1</version>
                        </dependency>
                    </dependencies>
```
```java
                    public class Main {
                    
                        public static void main(String[] args) {
                            
                            // 这里我们直接使用JedisSentinelPool来获取Master节点
                            // 需要把三个哨兵的地址都填入
                            try (JedisSentinelPool pool = new JedisSentinelPool("yxsnb", 
                                    new HashSet<>(Arrays.asList("192.168.0.8:26741", "192.168.0.8:26740", "192.168.0.8:26739")))) {
                                
                                Jedis jedis = pool.getResource(); // 直接询问并得到Jedis对象 这就是连接的Master节点
                                jedis.set("test", "114514"); // 直接写入即可 实际上就是向Master节点写入
                                
                                Jedis jedis2 = pool.getResource(); // 再次获取
                                System.out.println(jedis2.get("test")); // 读取操作
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        
                        }
                    
                    }
```

这样 Jedis对象就可以通过哨兵来获取 当Master节点更新后 也能得到最新的

### 集群搭建
如果我们服务器的内存不够用了 但是现在我们的Redis又需要继续存储内容 那么这个时候就可以利用集群来实现扩容

因为单机的内存容量最大就那么多 已经没办法再继续扩展了 但是现在又需要存储更多的内容 这时我们就可以让N台机器上的Redis来分别存储各个部分的数据
(每个Redis可以存储1/N的数据量) 这样就实现了容量的横向扩展 同时每台Redis还可以配一个从节点 这样就可以更好地保证数据的安全性

<img src="https://fast.itbaima.net/2023/03/07/TjCw8DLi1VqYvpZ.png"/>

那么问题来了 现在用户来了一个写入的请求 数据该写到哪个节点上呢? 我们来研究一下集群的机制:

首先 一个Redis集群包含16384个插槽 集群中的每个Redis实例负责维护一部分插槽以及插槽所映射的键值数据 那么这个插槽是什么意思呢?

实际上 插槽就是键的Hash计算后的一个结果 注意这里出现了计算机网络中的CRC循环冗余校验 这里采用CRC16 能得到16个bit位的数据 也就是说算出来之后结果是0-65535之间 再进行取模 得到最终结果

**Redis key的路由计算公式: slot = CRC16(key) % 16384**

结果的值是多少 就应该存放到对应维护的Redis下 比如Redis节点1负责0-25565的插槽 而这时客户端插入了一个新的数据a=10 a在Hash计算后结果为666
那么a就应该存放到1号Redis节点中 简而言之 本质上就是通过哈希算法将插入的数据分摊到各个节点的 所以说哈希算法真的是处处都有用啊

那么现在我们就来搭建应该简单的Redis集群 这里创建6个配置 注意开启集群模式:

```editorconfig
                    # Normal Redis instances can't be part of a Redis Cluster; only nodes that are
                    # started as cluster nodes can. In order to start a Redis instance as a
                    # cluster node enable the cluster support uncommenting the following:
                    #
                    cluster-enabled yes
```

接着记得把所有的持久化文件全部删除 所有的节点内容必须是空的

然后输入: redis-cli.exe --cluster create --cluster-replicas 1 127.0.0.1:6001 127.0.0.1:6002 127.0.0.1:6003 127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 
这里的--cluster-replicas 1指的是每个节点配一个从节点:

<img src="https://fast.itbaima.net/2023/03/07/7DikHoxKJPve9qa.png"/>

输入之后 会为你展示客户端默认分配的方案 并且会询问你当前的方案是否合理 可以看到6001/6002/6003都被选为主节点 其他的为从节点 我们直接输入yes即可:

<img src="https://fast.itbaima.net/2023/03/07/yxZhjouXB79qfDd.png"/>

最后分配成功 可以看到插槽的分配情况:

<img src="https://fast.itbaima.net/2023/03/07/8kg9TbadF2qyHJW.png"/>

现在我们随便连接一个节点 尝试插入一个值:

<img src="https://fast.itbaima.net/2023/03/07/YMf8DtlkCsqBpO1.png"/>

在插入时 出现了一个错误 实际上这就是因为a计算出来的哈希值(插槽) 不归当前节点管 我们得去管这个插槽的节点执行 通过上面的分配情况 我们可以得到15495属于节点6003管理:

<img src="https://fast.itbaima.net/2023/03/07/EZmR2bLFudSskIf.png"/>

在6003节点插入成功 当然我们也可以使用集群方式连接 这样我们无论在哪个节点都可以插入 只需要添加-c表示以集群模式访问:

<img src="https://fast.itbaima.net/2023/03/07/mVw7EXFJQOcinIb.png"/>

可以看到 在6001节点成功对a的值进行了更新 只不过还是被重定向到了6003节点进行插入

我们可以输入cluster nodes命令来查看当前所有节点的信息:

<img src="https://fast.itbaima.net/2023/03/07/pEJdI2UWTcNZFqu.png"/>

那么现在如果我们让某一个主节点挂掉会怎么样? 现在我们把6001挂掉:

<img src="https://fast.itbaima.net/2023/03/07/zd6L3WosVE8JUqf.png"/>

可以看到原本的6001从节点7001 普升为了新的主节点 而之前的6001已经挂了 现在我们将6001重启试试看:

<img src="https://fast.itbaima.net/2023/03/07/eUfYJS8yhVijvaw.png"/>

可以看到6001变成了7001的从节点 那么要是6001和7001都挂了呢?

<img src="https://fast.itbaima.net/2023/03/07/9W2BQtMXrUCnySV.png"/>

这时我们尝试插入新的数据:

<img src="https://fast.itbaima.net/2023/03/07/S8r5TE7gJ3M6iDW.png"/>

可以看到 当存在节点不可用时 会无法插入新的数据 现在我们将6001和7001恢复:

<img src="https://fast.itbaima.net/2023/03/07/2RL4GNqSWJXFuME.png"/>

可以看到恢复之后又可以继续正常使用了

最后我们来看一下如何使用Java连接到集群模式下的Redis 我们需要用到JedisCluster对象:

```java
                    public class Main {
                    
                        // 和客户端一样 随便连一个就行 也可以多写几个 构造方法有很多种可以选择
                        public static void main(String[] args) {
                    
                            tyr(JedisCluster cluster = new JedisCluster(new HostAndPost("192.168.0.8", 6003))) {
                                System.out.println("集群实例数量: " + Cluster.getClusterNodes().size());
                                cluster.set("a", "yyds");
                                System.out.println(Cluster.get("a"));
                            }
                    
                        }
                    
                    }
```

操作基本和Jedis对象一样 这里就不多做赘述了

### 分布式锁





















