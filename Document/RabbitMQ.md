<img src="https://image.itbaima.net/markdown/2023/03/08/9a2q4ZBuWxJs861.jpg"/>

### 消息队列
经过前面的学习 我们已经了解了我们之前的技术在分布式环境下的应用 接着我们来看最后一章的内容

那么 什么是消息队列呢?

我们之前如果需要进行远程调用 那么一般可以通过发送HTTP请求来完成 而现在 我们可以使用第二种方式 就是消息队列
它能够将发送方发送的信息放入队列中 当新的消息入队时 会通知接收方进行处理 一般消息发送方称为生产者 接收方称为消费者

<img src="https://image.itbaima.net/markdown/2023/03/08/yknBVt2jGgFSTO8.jpg"/>

这样我们所有的请求 都可以直接丢到消息队列中 再由消费者取出 不再是直接连接消费者的形式了 而是加了一个中间商 这也是一种很好的解耦方案 并且在高并发的情况下
由于消费者能力有限 消息队列也能起到一个削峰填谷的作用 堆积一部分的请求 再由消费者来慢慢处理 而不会像直接调用那样请求蜂拥而至

那么 消息队列具体实现有哪些呢:
- RabbitMQ - 性能很强 吞吐量很高 支持多种协议 集群化 消息的可靠执行特性等优势 很适合企业的开发
- Kafka - 提供了超高的吞吐量 ms级别的延迟 极高的可用性以及可靠性 而且分布式可以任意扩展
- RocketMQ - 阿里巴巴推出的消息队列 经历过双十一的考验 单机吞吐量高 消息的高可靠性 扩展性强 支持事务等 但是功能不够完整 语言支持性较差

我们这里 主要讲解的是RabbitMQ消息队列

### RabbitMQ消息队列
官方网站: https://www.rabbitmq.com

    RabbitMQ拥有数万计的用户 是最受欢迎的开源消息队列之一 从T-Mobile到Runtastic RabbitMQ在全球范围内用于小型初创企业和大型企业
    RabbitMQ轻量级 易于在本地和云端部署 它支持多种消息协议 RabbitMQ可以部署在分布式和联合配置中 以满足大规模 高可用性要求
    RabbitMQ在许多操作系统和云环境中运行 并为大多数流行语言提供了广泛的开发者工具

我们首先还是来看看如何进行安装

### 安装消息队列
下载地址: https://www.rabbitmq.com/download.html

由于除了消息队列本身之外还需要Erlang环境(RabbitMQ就是这个语言开发的) 所以我们就在我们的Ubuntu服务器上进行安装

首先是Erlang 比较大 1GB左右:

```shell
                    sudo apt install erlang
```

接着安装RabbitMQ:

```shell
                    sudo apt install rabbitmq-server
```

安装完成后 可以输入:

```shell
                    sudo rabbitmqctl status
```

来查看当前的RabbitMQ运行状态 包括运行环境, 内存占用, 日志文件等信息:

                        Runtime
    
                        OS PID: 13718
                        OS: Linux
                        Uptime (seconds): 65
                        Is under maintenance?: false
                        RabbitMQ version: 3.8.9
                        Node name: rabbit@ubuntu-server-2
                        Erlang configuration: Erlang/OTP 23 [erts-11.1.8] [source] [64-bit] [smp:2:2] [ds:2:2:10] [async-threads:64]
                        Erlang processes: 280 used, 1048576 limit
                        Scheduler run queue: 1
                        Cluster heartbeat timeout (net_ticktime): 60

这样我们的RabbitMQ服务器就安装完成了 要省事还得是Ubuntu啊

可以看到默认有两个端口被使用:

                        Listeners

                        Interface: [::], port: 25672, protocol: clustering, purpose: inter-node and CLI tool communication
                        Interface: [::], port: 5672, protocol: amqp, purpose: AMQP 0-9-1 and AMQP 1.0

我们一会主要使用的就是amqp协议的那个端口5672来进行连接 25672是集群化端口 之后我们也会用到

接着我们还可以将RabbitMQ的管理面板开启 这样的话就可以在浏览器上进行实时访问和监控了:

```shell
                    sudo rabbitmq-plugins enable rabbitmq_management
```

                        Listeners

                        Interface: [::], port: 25672, protocol: clustering, purpose: inter-node and CLI tool communication
                        Interface: [::], port: 5672, protocol: amqp, purpose: AMQP 0-9-1 and AMQP 1.0
                        Interface: [::], port: 15672, protocol: http, purpose: HTTP API

我们打开浏览器直接访问一下:

<img src="https://image.itbaima.net/markdown/2023/03/08/HxtXlqi7BUYWdC2.jpg"/>

可以看到需要我们进行登录才可以进入 我们这里还需要创建一个用户才可以 这里就都用admin:

```shell
                    sudo rabbitmqctl add_user 用户名 密码
```

将管理员权限给与我们刚刚创建好的用户:

```shell
                    sudo rabbitmqctl set_user_tags admin administrator
```

创建完成之后 我们登录一下页面:

<img src="https://image.itbaima.net/markdown/2023/03/08/eEJMsxhc5Onpld8.jpg"/>

进入之后会显示当前的消息队列情况 包括版本号 Erlang版本等 这里需要介绍一下RabbitMQ的设计架构 这样我们就知道各个模块管理的是什么内容了:

<img src="https://image.itbaima.net/markdown/2023/03/08/j5kIgD9ZRQiGtd6.jpg"/>

- `生产者(Publisher)和消费者(Consumer)`: 不用多说了吧
- `Channel`: 我们的客户端连接都会使用一个Channel 再通过Channel去访问到RabbitMQ服务器 注意通信协议不是http 而是amqp协议
- `Exchange`: 类似于交换机一样的存在 会根据我们的请求 转发给相应的消息队列 每个队列都可以绑定到Exchange上 这样Exchange就可以将数据转发给队列了 可以存在很多个 不同的Exchange类型可以用于实现不同消息的模式
- `Queue`: 消息队列本体 生产者所有的消息都存放在消息队列中 等待消费者取出
- `Virtual Host`: 有点类似于环境隔离 不同环境都可以单独配置一个Virtual Host 每个Virtual Host可以包含很多个Exchange和Queue 每个Virtual Host相互之间不影响

### 使用消息队列
我们就从最简单的模型开始讲起:

<img src="https://image.itbaima.net/markdown/2023/03/08/GWkUJx1g8ZnTV57.jpg"/>

(一个生产者 -> 消息队列 -> 一个消费者)

生产者只需要将数据丢进消息队列 而消费者只需要将数据从消息队列中取出 这样就实现了生产者和消费者的消息交互 我们现在来演示一下 首先进入到我们的管理页面 这里我们创建一个新的实验环境 只需要新建一个Virtual Host即可:

<img src="https://image.itbaima.net/markdown/2023/03/08/PzehXHuDyFANIKV.jpg"/>

添加新的虚拟主机之后 我们可以看到 当前admin用户的主机访问权限中新增了我们刚刚添加的环境:

<img src="https://image.itbaima.net/markdown/2023/03/08/9cGyunKrTvjfDRp.jpg"/>

现在我们来来看看Exchange(交换机):

<img src="https://image.itbaima.net/markdown/2023/03/08/GDnFoizW86pC5l9.jpg"/>

Exchange列表中自动为我们新增了刚刚创建好的虚拟主机相关的预设交换机 一共7个 这里我们首先介绍一下前面两个direct类型的交换机 一个是(AMQP default)还有一个是amq.direct 它们都是直连模式的交换机 我们来看看第一个:

<img src="https://image.itbaima.net/markdown/2023/03/08/lIpfxGjLPrOatE5.jpg"/>

第一个交换机是所有虚拟主机都会自带的一个默认交换机 并且此交换机不可删除 此交换机默认绑定到所有的消息队列 如果是通过默认交换机发送消息
那么会根据消息的routingkey(之后我们发消息都会指定)决定发送给哪个同名的消息队列 同时也不能显示地将消息队列绑定或解绑到此交换机

我们可以看到 详细信息中 当前交换机特性是持久化的 也就是说就算机器重启 那么此交换机也会保留 如果不是持久化 那么一旦重启就会消失
实际上我们在列表中看到D的字样 就表示此交换机是持久化的 包括一会我们要讲解的消息队列列表也是这样 所有自动生成的交换机都是持久化的

我们接着来看第二个交换机 这个交换机是一个普通的直连交换机:

<img src="https://image.itbaima.net/markdown/2023/03/08/DnpENxIPgOUTSbM.jpg"/>

这个交换机和我们刚刚介绍的默认交换机类型一致 并且也是持久化的 但是我们可以看到它是具有绑定关系的 如果没有指定的消息队列绑定到此交换机上
那么这个交换机无法正常将信息存放到指定的消息队列中 也是根据routingkey寻找消息队列(但是可以自定义)

我们可以在下面直接操作 让某个队列绑定 这里我们先不进行操作

介绍完了两个最基本的交换机之后(其他类型的交换机我们会在后面进行介绍) 我们接着来看消息队列:

<img src="https://image.itbaima.net/markdown/2023/03/08/q7WcUvZp8NhMb9f.jpg"/>

可以看到消息队列列表中没有任何的消息队列 我们可以来尝试添加一个新的消息队列:

<img src="https://image.itbaima.net/markdown/2023/03/08/D8hv6Kbo3eSNzVp.jpg"/>

第一行 我们选择我们刚刚创建好的虚拟主机 在这个虚拟主机下创建此消息队列 接着我们将其类型定义为Classic类型 也就是经典类型(其他类型我们会在后面逐步介绍) 名称随便起一个
然后持久化我们选择Transient暂时的(当然也可以持久化 看你自己) 自动删除我们选择No(需要至少有一个消费者连接到这个队列 之后 一旦所有与这个队列连接的消费者都断开时 就会自动删除此队列)
最下面的参数我们暂时不进行任何设置(之后会用到)

现在 我们就创建好了一个经典的消息队列:

<img src="https://image.itbaima.net/markdown/2023/03/08/yGSt4HbT7iX3Nze.jpg"/>

点击此队列的名称 我们可以查看详细信息:

<img src="https://image.itbaima.net/markdown/2023/03/08/NGCFKhcUf9lOADX.jpg"/>

详细信息中包括队列的当前负载状态, 属性, 消息队列占用的内存, 消息数量等 一会我们发送消息时可以进一步进行观察

现在我们需要将此消息队列绑定到上面的第二个直连交换机 这样我们就可以通过此交换机向此消息队列发送消息了:

<img src="https://image.itbaima.net/markdown/2023/03/08/NGCFKhcUf9lOADX.jpg"/>

这里填写之前第二个交换机的名称还有我们自定义的routingkey(最好还是和消息队列名称一致 这里是为了一会演示两个交换机的区别用) 我们直接点击绑定即可:

<img src="https://image.itbaima.net/markdown/2023/03/08/u95NJG2YskOBpXl.jpg"/>

绑定之后我们可以看到当前队列已经绑定对应的交换机了 现在我们可以前往交换机对此消息队列发送一个消息:

<img src="https://image.itbaima.net/markdown/2023/03/08/MBIDVqzf8oce2L4.jpg"/>

回到交换机之后 可以卡到这边也是同步了当前的绑定信息 在下方 我们直接向此消息队列发送信息:

<img src="https://image.itbaima.net/markdown/2023/03/08/htEoZ49zu6mipCM.jpg"/>

点击发送之后 我们回到刚刚的交换机详细页面 可以看到已经有一条新的消息在队列中了:

<img src="https://image.itbaima.net/markdown/2023/03/08/nO9eUjMRbCmBqPT.jpg"/>


























