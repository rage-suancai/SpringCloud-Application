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

我们可以直接在消息队列这边获取消息队列中的消息 找到下方的Get Message选项:

<img src="https://image.itbaima.net/markdown/2023/03/08/emrY3SZ98CJRAOh.jpg"/>

可以看到有三个选择 首先第一个Ack Mode 这个是应答模式选择 一共有四个选项:

<img src="https://image.itbaima.net/markdown/2023/03/08/nrWPuoGRTp7F36e.jpg"/>

- `Nack message requeue true`: 拒绝消息 也就是说不会将消息从消息队列取出 并且重新排队 一次可以拒绝多个消息
- `Ack message requeue true/false`: 确认应答 确认后消息会从消息队列中移除 一次可以确认多个消息
- `Reject message requeue true/false`: 也是拒绝此消息 但是可以指定是否重新排队

这里我们使用默认的就可以了 这样只会查看消息是啥 但是不会取出 消息依然存在于消息队列中 第二个参数是编码格式 使用默认的就可以了 最后就是要生效的操作数量 选择1就行:

<img src="https://image.itbaima.net/markdown/2023/03/08/c6auDXoHFqZT9M2.jpg"/>

可以看到我们刚刚的消息已经成功读取到

现在我们再去第一个默认交换机中尝试发送消息试试看:

<img src="https://image.itbaima.net/markdown/2023/03/08/t5V3yQ8kbOKRpxf.jpg"/>

如果我们使用之前自定义的柔routingkey 会显示没有路由 这是因为默认的交换机只会找对应名称的消息队列 我们现在向yyds发送一下试试看:

<img src="https://image.itbaima.net/markdown/2023/03/08/LCVPvykIjMox9m1.jpg"/>

可以看到消息成功发布了 我们来接收一下看看:

<img src="https://image.itbaima.net/markdown/2023/03/08/9jsdfADB5HRC7wP.jpg"/>

可以看到成功发送到此消息队列中了

当然除了在交换机发送消息给消息队列之外 我们也可以直接在消息队列这里发:

<img src="https://image.itbaima.net/markdown/2023/03/08/cYPwJnbiZlmvqD3.jpg"/>

效果是一样的 注意这里我们可以选择是否将消息持久化 如果是持久化消息 那么就算服务器重启 此消息也会保存在消息队列中

最后如果我们不需要再使用此消息队列了 我们可以手动对其进行删除或是清空:

<img src="https://image.itbaima.net/markdown/2023/03/08/kJE5xwgZOTGWjLq.jpg"/>

点击Delete Queue删除我们刚刚创建好的yyds队列 到这里 我们对应消息队列的一些简单使用 就讲解完毕了

### 使用Java操作消息队列
现在我们来看看如何通过Java连接到RabbitMQ服务器并使用消息队列进行消息发送(这里一起讲解 包括Java基础版本和SpringBoot版本) 首先我们使用最基本的Java客户端连接方式:

```xml
                    <dependency>
                        <groupId>com.rabbitmq</groupId>
                        <artifactId>amqp-client</artifactId>
                        <version>5.14.2</version>
                    </dependency>
```

依赖导入之后 我们来实现一下生产者和消费者 首先是生产者 生产者负责将信息发送到消息队列:

```java
                    public static void main(String[] args) {
                        
                        // 使用ConnectionFactory来创建连接
                        ConnectionFactory factory = new ConnectionFactory();
                
                        factory.setHost("192.168.43.128"); // 设定连接信息 基操
                        factory.setPort(5672); // 注意这里写5672 是amqp协议端口
                        factory.setUsername("admin");
                        factory.setPassword("admin");
                        factory.setVirtualHost("/test");
                        
                        // 创建连接
                        try (Connection connection = factory.newConnection()) {
                
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    
                    }
```

这里我们可以直接再程序中定义并创建消息队列(实际上是和我们在管理页面创建一样的效果) 客户端需要通过连接创建一个新的通道(Channel) 同一个连接下可以有很多个通道 这样就不用创建很多个连接也能支持分开发送了

```java
                    try (Connection connection = factory.newConnection();
                         Channel channel = connection.createChannel()) { // 通过Connection创建新的Channel
                        
                         // 声明队列 如果此队列不存在 会自动创建
                         channel.queueDeclare("yyds", false, false, false, null);
                         // 将队列绑定到交换机
                         channel.queueBind("yyds", "amq.direct", "my-yyds");
                         // 发布新的消息 注意消息需要转换为byte[]
                         channel.basicPublish("amq.direct", "my-yyds", null, "Hello RabbitMQ".getBytes());
            
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
```

其中queueDeclare方法的参数如下:
- queue: 队列的名称(默认创建后routingkey和队列名称一致)
- durable: 是否持久化
- exclusive: 是否排它 如果一个队列被声明为排它队列 该队列仅对首次声明它的连接可见 并在连接断开时自动删除 排它队列是基于Connection可见 同一个Connection的不同Channel是可以同时访问同一个连接创建的排它队列
             并且 如果一个Connection已经声明了一个排它队列 其它的Connection是不允许建立同名的排它队列的 即使该队列是持久化的 一旦Connection关闭或者客户端退出 该排它队列都会自动被删除
- autoDelete: 是否自动删除
- arguments: 设置队列的其它一些参数 这里我们暂时不需要什么其它参数

其中queueBind方法参数如下:
- queue: 需要绑定的队列名称
- exchange: 需要绑定的交换机名称
- routingkey: 不用说了吧

其中basicPublisg方法的参数如下:
- exchange: 对应的Exchange名称 我们这里就使用第二个直连交换机
- routingkey: 这里我们填写绑定时指定的routingkey 其实和之前在管理页面操作一样
- props: 其它的配置
- body: 消息本体

执行完成后 可以在管理页面中看到我们刚刚创建好的消息队列了:

<img src="https://image.itbaima.net/markdown/2023/03/08/baiDgVyoPQ65TMX.jpg"/>

并且此消息队列已经成功与amq.direct交换机进行绑定:

<img src="https://image.itbaima.net/markdown/2023/03/08/5lENjHswniC4Zg8.jpg"/>

那么现在我们的消息队列中已经存在数据了 怎么将其读取出来呢? 我们来看看如何创建一个消费者:

```java
                    public static void main(String[] args) {

                        ConnectionFactory factory = new ConnectionFactory();
                        factory.setHost("192.168.43.128");
                        factory.setPort(5672);
                        factory.setUsername("admin");
                        factory.setPassword("admin");
                        factory.setVirtualHost("/test");
                        
                        // 这里不使用try-with-resource 因为消费者是一直等待新的消息到来 然后按照 我们设定的逻辑进行处理 所以这里不能在定义完成之后就关闭连接
                        // 创建一个基本的消费者
                        channel.basicConsume("yyds", false, (s, delivery) -> {
                            System.out.println(new String(delivery.getBody()));
                            // basicAck是确认应答 第一个参数是当前的消息标签 后面的参数是: 是否批量处理消息队列中所有的消息 如果为false表示只处理当前消息
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                            // basicNack是拒绝应答 最后一个参数表示是否将当前消息放回队列 如果为false 那么消息就会被丢放
                            // channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                            // 跟上面一样 最后一个参数为false 只不过这里省了
                            // channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
                        }, s -> {});
            
                    }
```

其中basicConsume方法参数如下:
- queue - 消息队列名称 直接指定
- autoAck - 自动应答 消费者从消息队列取出数据后 需要跟服务器进行确认应答 当服务器收到确认后 会自动将消息删除 如果开启自动应答 那么消息发出后会直接删除
- deliver - 消息接收后的函数回调 我们可以在回调中对消息进行处理 处理完成后 需要给服务器确认应答
- cancel - 当消费者取消订阅时进行的函数回调 这里暂时用不到

现在我们启动一下消费者 可以看到立即读取我们刚刚插入到队列中的数据:

<img src="https://image.itbaima.net/markdown/2023/03/08/rR7eThxXbufjsEo.jpg"/>

我们现在继续在消息队列中插入新的数据 这里直接在网页上进行操作就行了 同样的我们也可以在消费者端接收并进行处理

现在我们把刚刚创建好的消息队列删除

官方文档: https://docs.spring.io/spring-amqp/docs/current/reference/html/

前面我们已经完成了RabbitMQ的安装和简单使用 并且通过Java连接到服务器 现在我们来尝试在SpringBoot中整合消息队列客户端 首先是依赖:

```xml
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-amqp</artifactId>
                    </dependency>
```

接着我们需要配置RabbitMQ的地址等信息:

```yaml
                    spring:
                      rabbitmq:
                        addresses: 192.168.43.128
                        username: admin
                        password: admin
                        virtual-host: /test
```

这样我们就完成了最基本信息配置 现在我们来看一下 如何像之前一样去声明一个消息队列 我们只需要一个配置类就行了:

```java
                    @Configuration
                    public class RabbitConfiguration {
                    
                        @Bean("directExchange") // 定义交换机Bean 可以很多个
                        public Exchange exchange() {
                            return ExchangeBuilder.directExchange("amp.direct").build();
                        }
                    
                        @Bean("yydsQueue") // 定义消息队列
                        public Queue queue() {
                    
                            return QueueBuilder
                                    .nonDurable("yyds") // 非持久化类型
                                    .build();
                    
                        }
                    
                        @Bean("binding")
                        public Binding binding(@Qualifier("directExchange") Exchange exchange,
                                               @Qualifier("yydsQueue") Queue queue) {
                            
                            // 将我们刚刚定义的交换机和队列进行绑定
                            return BindingBuilder
                                    .bind(queue) // 绑定队列
                                    .to(exchange) // 到交换机
                                    .with("my-yyds") // 使用自定义的routingKey
                                    .noargs();
                    
                        }
                    
                    }
```

接着我们来创建一个生产者 这里我们直接编写在测试用例中:

```java
                    @SpringBootTest
                    class SpringCloudMqApplicationTests {
                    
                        // RabbitTemplate为我们封装了大量的RabbitMQ操作 已经由Starter提供 因此直接注入使用即可
                        @Resource
                        RabbitTemplate template;
                    
                        @Test
                        void publisher() {
                            // 使用convertAndSend方法一步到位 参数基本和之前是一样的
                            // 最后一个消息本体可以是Object类型 真是大大的方便
                            template.convertAndSend("amq.direct", "my-yyds", "Hello World!");
                        }
                    
                    }
```

现在我们来运行一下这个测试用例:

<img src="https://image.itbaima.net/markdown/2023/03/08/UxVemu9B2cGifWv.jpg"/>

可以看到后台自动声明了我们刚刚定义好的消息队列和交换机以及对应的绑定关系 并且我们的数据也是成功插入到消息队列中:

<img src="https://image.itbaima.net/markdown/2023/03/08/RjY4JUn7v9pmryx.jpg"/>

现在我们再来看看如何创建一个消费者 因为消费者实际上就是一直等待消息然后进行处理的角色 这里我们只需要创建一个监听器就行了 它会一直等待消息到来然后再进行处理:

```java
                    @Component // 注册为Bean
                    public class TestListener {
                    
                        @RabbitListener(queues = "yyds") // 定义此方法为队列yyds的监听器 一旦监听器到新的消息 就会接收并处理
                        public void test(Message message) {
                            System.out.println(new String(message.getBody()));
                        }
                    
                    }
```

接着我们启动服务器:

<img src="https://image.itbaima.net/markdown/2023/03/08/ZjRs8u2cHbBEOaW.jpg"/>

可以看到控制台成功输出了我们之前放入队列的消息 并且管理页面中也显示此消费者已经连接了:

<img src="https://image.itbaima.net/markdown/2023/03/08/RwUFdgqXlDKk7AI.jpg"/>

接着我们再通过管理页面添加新的消息看看 也是可以正常进行接受的

当然 如果我们需要确保消息能够被消费者接收并处理 然后得到消费者的反馈 也是可以的:

```java
                    @Test
                    void publisher() {
    
                        // 会等待消费者消费然后返回响应结果
                        Object res = template.convertSendAndReceive("amq.direct", "my-yyds", "Hello World!");
                        System.out.println("收到消费者响应: " + res);
                        
                    }
```

消费者这边只需要返回一个对应的结果即可:

```java
                    @RabbitListener(queues = "yyds")
                    public String receiver(String data) {
                
                        System.out.println("一号消息队列监听器 " + data);
                        return "收到";
                
                    }
```

测试没有问题:

<img src="https://image.itbaima.net/markdown/2023/03/08/OkV6zN9PJRlwnQF.jpg"/>

那么如果我们需要直接接收一个JSON格式的消息 并且希望直接获取到实体类呢?

```java
                    @Data
                    public class User {
    
                        int id;
                        String name;
                        
                    }
```

```java
                    @Configuration
                    public class RabbitConfiguration {
    
                      	...
                    
                        @Bean("jacksonConverter") // 直接创建一个用于JSON转换的Bean
                        public Jackson2JsonMessageConverter converter(){
                            return new Jackson2JsonMessageConverter();
                        }
                        
                    }
```

接着我们只需要指定转换器就可以了:

```java
                    @Component
                    public class TestListener {
                    
                        // 指定messageConverter为我们刚刚创建的Bean名称
                        @RabbitListener(queues = "yyds", messageConverter = "jacksonConverter")
                        public void receiver(User user){ // 直接接收User类型
                            System.out.println(user);
                        }
                        
                    }
```

现在我们直接在管理页面发送:

```json
                    {"id":1,"name":"LB"}
```

!

<img src="https://image.itbaima.net/markdown/2023/03/08/3dXbs5naViUMrDO.jpg"/>

可以看到成功完成了转换 并输出了用户信息:

<img src="https://image.itbaima.net/markdown/2023/03/08/aM8SCL12hkKynUu.jpg"/>

同样的 我们也可以直接发送User 因为我们刚刚已经配置了Jackson2JsonMessageConverter为Bean 所以直接使用就可以了:

```java
                    @Test
                    void publisher() {
                        template.convertAndSend("amq.direct", "yyds", new User());
                    }
```

可以看到后台的数据类型为:

<img src="https://image.itbaima.net/markdown/2023/03/08/xVSpC7KHE1RyOk6.jpg"/>

<img src="https://image.itbaima.net/markdown/2023/03/08/Q9tBuprGwfleNLZ.jpg"/>

这样 我们就通过SpringBoot实现了RabbitMQ的简单使用

### 死信队列
消息队列中的数据 如果迟迟没有消费者来处理 那么就会一直占用消息队列的空间 比如我们模拟一下抢车票的场景 用户下单高铁票之后 会进行抢座 然后再进行付款
但是如果用户下单在后并没有及时的付款 这张票不可能一直让这个用户占用着 因为你不买别人还要买呢 所以会在一段时间后超时 让这张票可以继续被其他人购买

这时 我们就可以使用死信队列 将那些用户超时未付款的或是用户主动取消的订单 进行进一步的处理 以下类型的消息都会被判定为死信:
- 消息被拒绝(basic.reject/basic.nack)
- 消息TTL过期
- 队列达到最大长度

<img src="https://image.itbaima.net/markdown/2023/03/08/itUWySuA9kvcEgs.jpg"/>

那么如何构建这样的一种使用模式呢? 实际上本质就是一个死信交换机+绑定的死信队列 当正常队列中的消息被绑定为死信时 会被发送到对应的死信交换机 然后再通过交换机发送到死信队列中 死信队列也有对应的消费者去处理消息

这里我们直接再配置类中创建一个新的死信交换机和死信队列 并进行绑定:



















