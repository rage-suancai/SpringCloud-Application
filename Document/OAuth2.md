### 微服务应用
前面我们已经完成了SpringCloudAlibaba的学习 我们对一个微服务项目的架构体系已经有了一定的了解 那么本章我们将在应用层面继续探讨微服务

### 分布式权限校验
虽然完成前面的部分 我们已经可以去自己编写一个比较中规中矩的微服务项目了 但是还有一个问题我们没有解决
登录问题 假如现在要求用户登录之后 才能进行图书的查询, 借阅等操作 那么我们又该如何设计这个系统呢?

回顾我们之前进行权限校验的原理 服务器是如何判定一个请求是来自哪个用户的呢?
- 首先浏览器会向服务端发送请求 访问我们的网站
- 服务端收到请求后 会创建一个SESSION ID 并暂时存储在服务端 然后会发送给浏览器作为Cookie保存
- 之后浏览器会一直携带此Cookie访问服务器 这样在收到请求后 就能根据携带的Cookie中的SESSION ID判断是哪个用户了
- 这样服务端和浏览器之间可以轻松地建立会话了

但是我们想一下 我们现在采用的是分布式的系统 那么在用户服务进行登录之后 其他服务比如图书服务和借阅服务 它们会知道用户登录了吗?

<img src="https://fast.itbaima.net/2023/03/06/hV2JkERda4qKtjB.png"/>

实际上我们登录到用户服务之后 Session中的用户数据只会在用户服务的应用中保存 而在其他服务中 并没有对应的信息 但是我们现在希望的是
所有的服务都能够同步这些Session信息 这样我们才能实现现在用户服务登录之后其他服务都能知道 那么我们该如何实现Session的同步呢?

1. 我们可以在每台服务器上都复制一份Session 但是这样显然是浪费时间的 并且用户验证数据用的内存会成倍的增加

2. 将Session移出服务器 用统一存储来存放 比如我们可以直接在Redis或是MySQL中存放用户的Session信息 这样所有的服务器在需要获取Session信息时 
   统一访问Redis或是MySQL即可 这样就能保证所有服务都可以同步Session了(是不是越来越感觉只要有问题 没有什么是加一个中间件解决不了的)
    
   <img src="https://fast.itbaima.net/2023/03/06/pqZolFN6eIPza52.png"/>

那么 我们就着重来研究一下 然后实现2号方案 这里我们就使用Redis作为Session统一存储 我们把一开始的压缩包重新解压一次 又来从头开始编写吧

这里我们就只使用Nacos就行了 和之前一样 我们把Nacos的包导入一下 然后进行一些配置:

<img src="https://fast.itbaima.net/2023/03/06/FYcNvAuZ7z8rj2V.png"/>

现在我们需要每个服务都添加验证机制 首先导入依赖:

```xml
               <!-- SpringSession Redis支持 -->
               <dependency>
                   <groupId>org.springframework.session</groupId>
                   <artifactId>spring-session-data-redis</artifactId>
               </dependency>
               <!-- 添加Redis的Starter -->
               <dependency>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-starter-data-redis</artifactId>
               </dependency>
```

然后我们依然使用SpringSecurity框架作为权限校验框架:

```xml
               <dependency>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-starter-security</artifactId>
               </dependency>
```

接着我们在每个服务都编写一个对应的配置文件:

```yaml
                spring:
                  session:
                    # 存储类型修改为redis
                    store-type: redis
                  redis:
                    # Redis服务器的信息 该咋写咋写
                    host: 1.14.121.107
```

这样 默认情况下 每个服务的接口都会被SpringSecurity所保护 只有登录成功之后 才可以被访问

我们来打开Nacos看看:

<img src="https://fast.itbaima.net/2023/03/06/SyCJXKgO3qGx8EL.png"/>

可以看到三个服务都正常注册了 接着我们去访问图书服务:

<img src="https://fast.itbaima.net/2023/03/06/gytqnZjTMvVEUm3.png"/>

可以看到 访问失败直接把我们给重定向到登录页面了 也就是说必须登录之后能访问 同样的方式去访问其他服务 也是一样的效果

由于现在是统一Session存储 那么我们就可以在任意一个服务登录之后 其他服务都可以正常访问 现在我们在当前页面登录 登录之后可以看到图书服务能够正常访问了:

<img src="https://fast.itbaima.net/2023/03/06/xfV5oYGvc1jKqTM.png"/>

同时用户访问也能正常访问了:

<img src="https://fast.itbaima.net/2023/03/06/OH6wjLVreot4IiA.png"/>

我们可以查看一下Redis服务器中是不是存储了我们的Session信息:

<img src="https://fast.itbaima.net/2023/03/06/nNIkoXOAYuMH8aV.png"/>

虽然看起来好像确实没啥问题了 但是借阅服务炸了 我们来看看为什么:

<img src="https://fast.itbaima.net/2023/03/06/wls5vCajnuMBOkU.png"/>

在RestTemplate进行远程调用的时候 由于我们的请求没有携带对应SESSION的Cookie 所以导致验证失败
服务不成功 返回401 所以虽然这种方案看起来比较合理 但是在我们的实际使用中 还是存在一些不便的

### OAuth2.0 实现单点登录
注意: 第一次接触可能会比较难 不太好理解 需要多实践和观察

前面我们虽然使用了统一存储来解决Session共享问题 但是我们发现就算实现了Session共享 依然存在一些问题 由于我们每个服务都有自己的验证模块
实际上整系统是存在冗余功能的 同时还有我们上面出现的问题 那么能否实现只在一个服务进行登录 就可以访问其他服务呢?

<img src="https://fast.itbaima.net/2023/03/06/46ukOAiDzMZBX15.png"/>

实际上之前的登录模式称为多点登录 而我们希望的是实现单点登录 因此 我们得找一个更好的解决方案

这里我们首先需要了解一种全新的登录方式: OAuth2.0 我们经常看到一些网站支持第三方登录 比如淘宝, 咸鱼我们就可以使用支付宝进行登录 腾讯游戏可以用QQ或是微信登录
以及微信小程序都可以直接使用微信进行登录 我们知道它们并不是属于同一个系统 比如淘宝和咸鱼都不属于支付宝这个应用 但是由于需要获取支付宝的用户信息
这时我们就需要使用 OAuth2.0 来实现第三方授权 基于第三方应用访问用户信息的权限(本质上就是给别人调用自己服务接口的权限) 那么它是如何实现的呢?

### 四种授权模式
我们还是从理论开始讲解 OAuth2.0一共有四种授权模式:

1. **客户端模式(Client Credentials)**
   这是最简单的一种模式 我们可以直接向验证服务器请求一个Token(这里可能有些小伙伴对Token的概念不是很熟悉 Token相当于是一个令牌 我们需要在验证服务器
   (User Account And Authe ntication) 服务拿到令牌之后 才能去访问资源 比如用户信息, 借阅信息等 这样资源服务器才能知道我们是谁以及是否成功登录了)
   
   当然 这里的前端页面只是一个例子 它还可以是其他任何类型的客户端 比如App, 小程序甚至是第三方应用的服务

   <img src="https://fast.itbaima.net/2023/03/06/4i16wzqOnYeaB2c.png"/>

   虽然这种模式比较简便 但是已经失去了用户验证的意义 压根就不是给用户校验准备的 而是更适用于服务内部调用的场景


2. **密码模式(Resource Owner Password Credentials)**
   密码模式相比客户端模式 就多了用户名和密码的信息 用户需要提供对应账号的用户名和密码 才能获取到Token

   <img src="https://fast.itbaima.net/2023/03/06/JEreS9nQD8ojMca.png"/>

   虽然这样看起来比较合理 但是会直接将账号和密码泄露给客户端 需要后台完全信任客户端不会拿账号密码去干其他坏事 所以这也不是我们常见的


3. **隐式授权模式(Implicit Grant)**
   首先用户访问页面时 会重定向到认证服务器 接着认证服务器给用户一个认证页面 等待用户授权 用户填写信息完成授权后 认证服务器返回Token

   <img src="https://fast.itbaima.net/2023/03/06/MRxnKyWT3br5Zj2.png"/>

   它适用于没有服务端的第三方应用页面 并且相比前面一种形式 验证都是在验证服务器进行的 敏感信息不会轻易泄露 但是Token依然存在泄露的风险


4. **授权码模式(Authrization Code)**
   这种模式是最安全的一种模式 也是推荐使用的一种 比如我们手机上很多App都是使用的这种模式
   相比隐式授权模式 它并不会直接返回Token 而是返回授权码 真正的Token是通过应用服务器访问验证服务器获取的 在一开始的时候 应用服务器
   (客户端通过访问自己的应用服务器来进而访问其他服务)和验证服务器之间会共享一个secret 这个东西没有其它人知道 而验证服务器在用户验证完成之后 
   会返回一个授权码 应用服务器最后将授权码和secret一起交给验证服务器进行验证 并且Token也是在服务端之间传递 不会直接给到客户端

   <img src="https://fast.itbaima.net/2023/03/06/2EIPfirBOKbcndk.png"/>

   这样就算有人中途窃取了授权码 也毫无意义 因为 Token的获取必须同时携带授权码和secret 但是secret第三方是无法得知的 并且Token不会直接丢给客户端 大大减少了泄露的风险

是不是乍一看 OAuth2.0不应该是那种第三方应用为了请求我们的服务而使用的吗 而我们这里需要的只是实现同一个应用内部服务之间的认证
其实我们也可以利用OAuth2.0来实现单点登录 只是少了资源服务器这一角色 客户端就是我们的整个系统 接下来就让我们来实现一下

### 搭建验证服务器
第一步就是最重要的 我们需要搭建一个验证服务器 它是我们进行权限校验的核心 验证服务器有很多的第三方实现也有Spring官方提供的实现 这里我们使用Spring官方提供的验证服务器

这里我们将最开始保存好的项目解压 就重新创建一个新的项目 首先我们在父项目中添加最新的SpringCloud依赖:

```xml
               <dependency>
                   <groupId>org.springframework.cloud</groupId>
                   <artifactId>spring-cloud-dependencies</artifactId>
                   <version>2021.0.1</version>
                   <type>pom</type>
                   <scope>import</scope>
               </dependency>
```

接着创建一个新的模块auth-service 添加依赖:

```xml
               <dependencies>
                   <dependency>
                       <groupId>org.springframework.boot</groupId>
                       <artifactId>spring-boot-starter-web</artifactId>
                   </dependency>
               
                   <dependency>
                       <groupId>org.springframework.boot</groupId>
                       <artifactId>spring-boot-starter-security</artifactId>
                   </dependency>
                   
                   <!-- OAuth2.0依赖 不再内置了 所以得我们自己指定一下版本 -->
                   <dependency>
                       <groupId>org.springframework.cloud</groupId>
                       <artifactId>spring-cloud-starter-oauth2</artifactId>
                       <version>2.2.5.RELEASE</version>
                   </dependency>
               </dependencies>
```

接着我们修改一下配置文件:

```yaml
               server:
                 port: 8500
                 servlet: 
                   # 为了防止一会在服务之间跳转导致Cookie打架(因为所有服务地址都是localhost 都会存JSESSIONID)
                   # 这里修改一下context-path 这样保存的Cookie会使用指定的路径 就不会和其它服务打架了
                   # 但是注意之后的请求都掉在最前面加上这个路径
                   context-path: /sso
```

接着我们需要编写一下配置类 这里需要两个配置类 一个是OAuth2的配置类 还有一个是SpringSecurity的配置类:

```java
               @Configuration
               public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
               
                   @Override
                   protected void configure(HttpSecurity http) throws Exception {
                       
                       http
                               .authorizeRequests()
                               .anyRequest().authenticated() // 
                               
                               .and()
                               
                               .formLogin().permitAll(); // 使用表单登录
                       
                   }
               
                   @Override
                   protected void configure(AuthenticationManagerBuilder auth) throws Exception {
               
                       BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                       auth
                               .inMemoryAuthentication() // 直接创建一个用户 懒得搞数据库了
                               .passwordEncoder(encoder)
                               .withUser("test").password(encoder.encode("123456")).roles("user");
                       
                   }

                   @Bean // 这里需要将AuthenticationManager注册为Bean 在OAuth配置中使用
                   @Override
                   public AuthenticationManager authenticationManagerBean() throws Exception {
                      return super.authenticationManagerBean();
                   }
                   
               }
```
```java
               @EnableAuthorizationServer // 开启验证服务器
               @Configuration
               public class OAuth2Configuration extends AuthorizationServerConfigurerAdapter {
               
                   @Resource
                   private AuthenticationManager manager;
               
                   private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

                   /**
                    * 这个方法是对客户端进行配置 一个验证服务器可以预设很多客户端
                    * 之后这些指定的客户端就可以按照下面指定的方式进行验证
                    * @param clients 客户端配置工具
                    */
                   @Override
                   public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
               
                       clients
                               .inMemory() // 这里我们直接硬编码创建 当然也可以像Security那样自定义或是使用JDBC从数据库读取
                               .withClient("web") // 客户端名称 随便起就行
                               .secret(encoder.encode("654321")) // 只与客户端分享的ssecret 随便写但是注意要加密
                               .autoApprove(false) // 自动审批 这里关闭 要的就是一会体验那种感觉
                               .scopes("book", "user", "borrow") // 授权范围 这里我们使用全部all
                               .authorizedGrantTypes("client_credentials", "password", "implicit", "authorization_code", "refresh_token");
                               // 授权模式 一共支持5种 除了之前我们介绍的四种之外 还有一个刷新Token的模式
                               // 这里我们直接把五种都写上 方便一会实验 当然各位也可以单独只写一种一个一个进行测试
                               // 现在我们指定的客户端就支持这五种类型的授权方式了
                      
                   }
               
                   @Override
                   public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
               
                       security
                               .passwordEncoder(encoder) // 编码器设定为BCryptPasswordEncoder
                               .allowFormAuthenticationForClients() // 允许客户端使用表单验证 一会我们POST请求中会携带表单信息
                               .checkTokenAccess("permitAll()"); // 允许所有的Token查询请求
               
                   }
               
                   @Override
                   public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
               
                       endpoints
                               .authenticationManager(manager);
                       // 由于SpringSecuriry新版本的一些底层改动 这里需要配置一下authenticationManager 才能正常使用password模式
                      
                   }
               
               }
```

接着我们就可以启动服务器了:

<img src="https://fast.itbaima.net/2023/03/06/2FhnOKe1BorP5NE.png"/>

然后我们使用Postman进行接口测试 首先我们从最简单的客户端进模式行测试 客户端模式只需要提供id和secret即可直接拿到Token
注意: 需要再添加一个grant_type来表明我们的授权方式 默认请求路径为: http://localhost:8500/sso/oauth/token:

<img src="https://fast.itbaima.net/2023/03/06/X81T7mz5gQK3iBk.png"/>

发起请求后 可以看到我们得到了Token 它是以JSON格式给到我们的:

<img src="https://fast.itbaima.net/2023/03/06/84IKgq2xdvBeLTm.png"/>

我们还可以访问 http://localhost:8500/sso/oauth/check_token 来验证我们的Token是否有效:

<img src="https://fast.itbaima.net/2023/03/06/SXD8FjzZn7ev2B3.png"/>

<img src="https://fast.itbaima.net/2023/03/06/B9TzojnUq4KvVPr.png"/>

可以看到active为true 表示我们刚刚申请到的Token是有效的

接着我们来测试一下第二种password模式 我们还需要提供具体的用户名和密码 授权模式定义为password即可:

<img src="https://fast.itbaima.net/2023/03/06/jt5XPZKvRFqr73x.png"/>

接着我们需要在请求头中添加Basic验证信息 这里我们直接填写id和secret即可:

<img src="https://fast.itbaima.net/2023/03/06/K9ZpIv8SzcfsHd4.png"/>

可以看到在请求头中自动生成了Basic验证相关内容:

<img src="https://fast.itbaima.net/2023/03/06/JHxPKgFU5wY7SB8.png"/>

<img src="https://fast.itbaima.net/2023/03/06/F3WU7XhqridywVn.png"/>

响应成功 得到Token信息 并且这里还多出了一个refresh_token 这是用于刷新Token的 我们之后会进行讲解

<img src="https://fast.itbaima.net/2023/03/06/zjuc2qxQmBas5r1.png"/>

查询Token信息之后还可以看到登录的具体用户以及角色权限等

接着我们来看隐式授权模式 这种模式我们需要在验证服务器上进行登录操作 而不是直接请求Token
验证登录请求地址: http://localhost:8500/sso/oauth/authorize?client_id=web&response_type=token

注意response_type一定要是token类型 这样才会直接返回Token 浏览器发起请求后 可以看到熟悉而又陌生的界面 没错
实际上这里就是使用我们之前讲解的SpringSecurity进行登录 当然也可以配置一下记住我之类的功能 这里就不演示了:

<img src="https://fast.itbaima.net/2023/03/06/OYeRQpEXFSoZMhc.png"/>

但是登录之后我们发现出现了一个错误:

<img src="https://fast.itbaima.net/2023/03/06/qLUkJFZau8eQ6WO.png"/>

这是因为登录成功之后 验证服务器需要将结果给回客户端 所以需要提供客户端的回调地址 这样 浏览器就会被重定向到指定的回调地址并且请求中会携带Token信息 这里我们随便配置一个回调地址:

```java
               @Override
               public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
                   
                   clients
                       .inMemory()
                       .withClient("web")
                       .secret(encoder.encode("654321"))
                       .autoApprove(false)
                       .scopes("book", "user", "borrow")
                       .redirectUris("http://localhost:8201/login") // 可以写多个 当有多个时需要在验证请求中指定使用哪个地址进行回调
                       .authorizedGrantTypes("client_credentials", "password", "implicit", "authorization_code", "refresh_token");
                   
               }
```

接着重启验证服务器 再次访问:

<img src="https://fast.itbaima.net/2023/03/06/PnTwQhlYXDgBvry.png"/>

可以看到这里会让我们选择哪些范围进行授权 就像我们在微信小程序中登录一样 会让我们授予用户信息权限, 支付权限, 信用查询权限等
我们可以自由决定要不要给客户端授予访问这些资源的权限 这里我们全部选择授予:

<img src="https://fast.itbaima.net/2023/03/06/p7nMEVZIKjXWAl5.png"/>

授予之后 可以看到浏览器被重定向到我们刚刚指定的回调地址中 并且携带了Token信息 现在我们来校验一下看看:

<img src="https://fast.itbaima.net/2023/03/06/g1JhS9WDfcz6QEK.png"/>

可以看到 Token也是有效的

最后我们来看看第四种最安全的授权码模式 这种模式其实流程和上面是一样的 但是请求的是code类型: http://localhost:8500/sso/oauth/authorize?client_id=web&response_type=code

可以看到访问之后 依然会进入到回调地址 但是这时给的就是授权码了 而不是直接给Token 那么这个Token该怎么获取呢?

<img src="https://fast.itbaima.net/2023/03/06/da4WseDt172hbLV.png"/>

按照我们之前讲解的原理 我们需要携带授权码和secret一起请求 才能拿到Token 正常情况下是有由回调的服务器进行处理
这里我们就在Postman中进行 我们复制刚刚得到的授权码 接口依然是localhost:8500/sso/oauth/token:

<img src="https://fast.itbaima.net/2023/03/06/e1Zdt9IP7vp2zMO.png"/>

可以看到结果也是正常返回了Token信息:

<img src="https://fast.itbaima.net/2023/03/06/qY5kxgBWSzMJXco.png"/>

这样我们四种最基本的Token请求方式就实现了

最后还有一个是刷新令牌使用的 当我们的Token过期时 我们就可以使用这个refresh_token来申请一个新的Token:

<img src="https://fast.itbaima.net/2023/03/06/d2ojclCLB3mQu7D.png"/>

但是执行之后我们发现会直接出现一个内部错误:

<img src="https://fast.itbaima.net/2023/03/06/BcFMIg4NqCx8kdh.png"/>

<img src="https://fast.itbaima.net/2023/03/06/cA9WF1KxyUDZ8Bi.png"/>

查看日志发现 这里还需要我们单独配置一个UserDetailsService 我们直接把Security中的实例注册为Bean:

```java
               @Bean
               @Override
               public UserDetailsService userDetailsServiceBean() throws Exception {
                  return super.userDetailsServiceBean();
               }
```

然后在Endpoint中设置:

```java
               @Resource
               private UserDetailsService service;
               
               @Override
               public void configure(AuthorizationServerEndpointsConfigurer endpoints) {

                  endpoints
                          .userDetailsService(service)
                          .authenticationManager(manager);

               }
```

最后再次尝试刷新Token:

<img src="https://fast.itbaima.net/2023/03/06/QWEwzpiq7FXnv3f.png"/>

OK 成功刷新Token 返回了一个新的

### 基于@EnableOAuth2Sso实现
前面我们将验证服务器已经搭建完成了 现在我们就来实现一下单点登录吧 SpringCloud为我们提供了客户端的直接实现
我们只需要添加一个注解和少量配置即可将我们的服务作为一个单点登录应用 使用的是第四种授权码模式

一句话来说就是 这种模式只是将验证方式由原来的默认登录形式改变为了统一在授权服务器登录的形式

首先还是依赖:

```xml
               <dependency>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-starter-security</artifactId>
               </dependency>
               
               <dependency>
                   <groupId>org.springframework.cloud</groupId>
                   <artifactId>spring-cloud-starter-oauth2</artifactId>
                   <version>2.2.5.RELEASE</version>
               </dependency>
```

我们只需要直接在启动类上添加即可:

```java
               @EnableOAuth2Sso
               @SpringBootApplication
               public class BookApplication {

                  public static void main() {
                     SpringApplication.run(BookApplciation.class, args);
                  }

               }
```

我们不需要进行额外的配置类 因为这个注解已经帮我们做了:

```java
               @Target({ElementType.TYPE})
               @Retention(RetentionPolicy.RUNTIME)
               @Documented
               @EnableOAuth2Client
               @EnableConfigurationProperties({OAuth2SsoProperties.class})
               @Import({OAuth2SsoDefaultConfiguration.class, OAuth2SsoCustomConfiguration.class, ResourceServerTokenServicesConfiguration.class})
               public @interface EnableOAuth2Sso {
               }
```

可以看到它直接注册了OAuth2SsoDefaultConfiguration 而这个类就是帮助我们对Security进行配置的:

```java
               @Configuration
               @Conditional({NeedsWebSecurityCondition.class})
               public class OAuth2SsoDefaultConfiguration extends WebSecurityConfigurerAdapter {
               // 直接继承的WebSecurityConfigurerAdapter 帮我们把验证设置都写好了
               private final ApplicationContext applicationContext;
               
                   public OAuth2SsoDefaultConfiguration(ApplicationContext applicationContext) {
                       this.applicationContext = applicationContext;
                   }
```
                   
接着我们需要在配置文件中配置我们的验证服务器相关信息:

```yaml
                security:
                  oauth2:
                    client:
                      # 不多说了
                      client-id: web
                      client-secret: 654321
                      # Token获取地址
                      access-token-uri: http://localhost:8500/sso/oauth/teken
                      # 验证页面地址
                      user-authorization-uri: http://localhost:8500/sso/oauth/authorize
                    resource:
                       # Token信息获取和校验地址
                      token-info-uri: http://localhost:8500/sso/oauth/check_token
```

现在我们就开启图书服务 调用图书接口:

<img src="https://fast.itbaima.net/2023/03/06/DrVSZtdKNCoMucx.png"/>

可以看到在发现没有登录验证时 会直接跳转到授权页面 进行授权登录 之后才可以继续访问图书服务:

<img src="https://fast.itbaima.net/2023/03/06/nsJGmxcOVYXDUqd.png"/>

那么用户信息呢? 是否也一并保存过来了? 我们这里直接获取一下SpringSecurity的Context查看用户信息 获取方式跟我们之前讲解的是一样的:

```java 
               @RequestMapping("/book/{bid}")
               Book findBookById(@PathVariable("bid") int bid){
    
                   // 通过SecurityContextHolder将用户信息取出
                   SecurityContext context = SecurityContextHolder.getContext();
                   System.out.println(context.getAuthentication());
                   return service.getBookById(bid);
                   
               }
```

<img src="https://fast.itbaima.net/2023/03/06/y1VYRC9tmOv854u.png"/>

这里使用的不是之前的UsernamePasswordAuthenticationToken也不是RememberMeAuthenticationToken
而是新的OAuth2Authentication 它保存了验证服务器的一些信息 以及经过我们之前的登录流程之后 验证服务器发放给客户端的Token信息
并通过Token信息在验证服务器进行验证获取用户信息 最后保存到Session中 表示用户已验证 所以本质上还是要依赖浏览器存Cookie的

接下来我们将所有的服务都使用这种方式进行验证 别忘了把重定向地址给所有服务都加上:

```java
               @Override
               public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
                   
                   clients
                           .inMemory()
                           .withClient("web")
                           .secret(encoder.encode("654321"))
                           .autoApprove(true) // 这里把自动审批开了 就不用再去手动选同意了
                           .scopes("book", "user", "borrow")
                           .redirectUris("http://localhost:8101/login", "http://localhost:8201/login", "http://localhost:8301/login")
                           .authorizedGrantTypes("client_credentials", "password", "implicit", "authorization_code", "refresh_token");
                           
               }
```

这样我们就可以实现只在验证服务器登录 如果登录过其它的服务都可以访问了

但是我们发现了一个问题 就是由于SESSION不同步 每次切换不同的服务进行访问都会重新导致验证访服务去验证一次:

<img src="https://fast.itbaima.net/2023/03/06/7zbqlOrSCVRdQ4y.png"/>

这里有两个方案:
- 像之前一样做SESSION统一存储
- 设置context-path路径 每个服务单独设置 就不会打架了

但是这样依然没法解决服务间调用的问题 所以仅仅依靠单点登录的模式不太行

### 基于@EnableResourceServer实现
前面我们讲解了将我们的服务作为单点登录应用直接实现单点登录 那么现在我们如果是以第三方应用进行访问呢? 这时我们就需要将我们的服务作为资源服务了
作为资源服务就不会再提供验证的过程 而是直接要求请求时携带Token 而验证过程我们这里就继续用Postman来完成 这才是我们常见的模式

一句话来说 跟上面相比 我们只需要携带Token就能访问这些资源服务器了 客户端被独立出来 用于携带Token去访问这些服务

我们也只需要添加一个注解和少量配置即可:

```java

```

















