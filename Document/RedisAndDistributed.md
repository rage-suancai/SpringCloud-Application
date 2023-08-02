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

一个服务器的端口设定为6001 复制[redis.windows.conf](..%2F..%2F..%2F..%2F..%2F..%2F..%2Fcode-software%2Fjar%20gather%2FJAVA-exploitation%2FRedis%2Fredis%2Fredis.windows.conf)一份 另一个的端口为6002 接着我们指定配置文件进行启动 打开cmd:

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






















