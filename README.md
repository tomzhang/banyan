##名词解释
- message carry: 消息的produce / consume 被抽象为carry(表示消息的 **搬运** )
- message format: object/text等，注意此处没有称之为message type，请注意区分
- message type: business/system 按照业务逻辑来划分

##消息传输
消息传输分为produce / consume 被抽象为两个接口：

* IProducer: 定义了生产消息的接口
* IConsumer: 定义了消费消息的接口
* AbstractMessageCarryer: 抽象了消息传输的共性部分（主要包含handler-chain的实例化）

继承关系图如下：
![img 2][2]



##消息格式
参照jms规范，目前messagebus支持如下五种消息：

* Stream - 流
* Bytes - 字节数组
* Map - map(键值对)
* Object - 对象类型
* Text - 文本类型

继承关系如下图：

![img 1][1]

消息中间件默认只接受byte[]，因此需要对以上支持的消息进行格式化，这部分对应的继承关系图：
![img 3][3]

其中：

* IFormatter: 为消息格式化器接口，提供了消息格式化的两个契约方法：
    - format(Message): 为消息格式化方法，用于produce
    - deFormat(byte[]): 为消息反格式化方法，用于consume
* FormatterFactory: 提供了formatter的创建工厂

##消息的链式处理
消息的链式处理，有利于切割处理模块，方便拆分功能等。我认为这种方式应该是以数据为处理核心的业务模型的首选。
这部分的继承关系图如下：
![img 4][4]

AbstractHandler处于继承类的顶端，为一个抽象的处理器，它定义了三个方法：

* init: 实例化方法，在handler从配置文件中读取并初始化的时候被调用
* handle: 每个继承它的handler所必须实现的 **抽象** 方法，是实现handler-chain的关键
* destroy: 释放资源的触发方法，将在“关闭”messagebus client的时候被逐一调用

从上面的图示可以看到，所有的handler被分为三大类（分别位于三个package中）:

* common: 公共handler包，用于封装p & c都需要处理的逻辑，比如参数校验等
* produce: 在生产消息过程中，需要的handler
* consumer: 在发送消息过程中，需要的handler

目前已经支持的handler的文件目录结构图
![img 5][5]

当然，他们的先后顺序并不是定死的，而是依赖于配置：
![img 6][6]

前面提到的AbstractHandler以及所有的子handler都是用于承载业务实现的，但要串联起它们，就需要另一个接口：

* IHandlerChain: 定义了一个handle方法，用于实现handler-chain
* MessageCarryHandlerChain: 实现了 ***IHandlerChain*** ,并构建了一个用于消息传输的处理器链

##消息处理的上下文对象
之前提到了消息的处理是基于链式（或者称之为流式）的，但要让这些handler在技术层面上能够得以实现，一个承载了它们都需要用到的数据的上下文对象必不可少。
这里的上下文对象，就是所谓的“Big Object”，结构图如下：

![img 7][7]

因为这个对象主要流通于各个handler之间，为共享数据为目的。如果你需要设置额外的对象，而context原先并不包含，那么你可以通过其中的otherParams属性来扩展你需要传输的数据它接收键值对集合<String, Object>。

##Channel的对象池
如果有大批量或生产消息密集型的业务需求，那么每次生产消息都创建然后再销毁用于发送消息的channel，有些过于浪费。channel的初始化涉及到socket的通信、连接的建立，毫无疑问它也是那种真正意义上的“big object”。所以在高频率使用的场景下，池化channel无疑能够提供更好的性能。
此处，对象池的底层支持基于apache的common-pool，它的实现位于 ***com.freedom.messagebus.client.core.pool*** package下，关键对象：
![img 8][8]

> PooledChannelAccessor 并不是Pool实现的一部分，它是一个handler，应该说是使用者。

* AbstractPool: 一个被实现为泛型的通用抽象pool，它以 ***face pattern*** 实现，内部以一个GenericObjectPool的实例来提供服务
* ChannelPool: 继承自AbstractPool，并对其进行了具体化以支持对channel的cache
* ChannelPoolConfig: 继承自GenericObjectPoolConfig，用于对pool进行一些设置，这里预先进行了部分设置，还有部分位于配置文件
* ChannelFactory: 继承自PooledObjectFactory，用于创建被 **包装** 过的真实对象

##远程配置与管控
可以看到在项目的resources文件夹下包含有多个配置文件，这里，他们存在的目的主要是初期构建的需要。等到真正使用的时候，他们会被“无视”或者是成为失效备援而采用。所有的配置都将从远程获取，这依赖于用zooKeeper实现的远程配置集群。
目前初步构建的远程zookeep节点示例：
![img 9][9]

拆分为两个部分来看：

* common-config: client通用配置，比如对象池配置、path配置、queue的命名名称配置等
* proxy: 队列的“个性化配置”，此节点的子树跟route topology基本一致，需要注意的是只有队列（图中该分支里的圆形）才拥有这些个性化的配置。用于客户端对发往这些队列的消息进行管控

> 挂有"data"的数据节点才表示某节点带有配置，exchange通常不需要有配置（但如果需要也可以有）

客户端准备carry message的时候，需要传入zookeeper的server host 以及 port。紧接着，会从remote端同步最新的config，然后实例化本地的config manager。与此同时，会建立起长连接的zookeeper client（见 **LongLiveZookeeper** ），它会根据同步过来的path列表，侦听所有server端的这些配置，一旦有任何对队列配置的修改，就会被同步到client来，这样就可以在client即时应用这些策略，来对其加以管控。这可以实现很多需求，比如：

```
* 禁止往erp队列发送任何消息
* 只可以往crm队列发送text消息
* 往oa.sms里发送的消息不得大于500K
* 往oa.email里发送消息的速率不得大于10条/s
* ...
```

##调用示例

```java
public void testSimpleProduceAndConsume() throws Exception {
        Messagebus client = Messagebus.getInstance();
        client.setZkHost("localhost");
        client.setZkPort(2181);
        client.open();

        //start consume
        appkey = java.util.UUID.randomUUID().toString();
        msgType = "business";
        String queueName = "oa.sms";
        IConsumerCloser closer = client.getConsumer().consume(appkey, msgType, queueName,
                                                              new IMessageReceiveListener() {
            @Override
            public void onMessage(Message msg, MessageFormat format) {
                switch (format) {
                    case Text: {
                        TextMessage txtMsg = (TextMessage) msg;
                        logger.debug("received message : " + txtMsg.getMessageBody());
                    }
                    break;

                    case Object: {
                        ObjectMessage objMsg = (ObjectMessage) msg;
                        SimpleObjectMessagePOJO realObj = (SimpleObjectMessagePOJO) objMsg.getObject();
                        logger.debug("received message : " + realObj.getTxt());
                    }
                    break;

                    //case other format
                    //...
                }
            }
        });

        //produce text msg
        TextMessagePOJO msg = new TextMessagePOJO();
        msg.setMessageBody("just a test");
        client.getProducer().produce(msg, MessageFormat.Text, appkey, queueName, msgType);

        //produce object msg
        ObjectMessagePOJO objMsg = new ObjectMessagePOJO();
        SimpleObjectMessagePOJO soPojo = new SimpleObjectMessagePOJO();
        soPojo.setTxt("test object-message");
        objMsg.setObject(soPojo);
        client.getProducer().produce(objMsg, MessageFormat.Object, appkey, queueName, msgType);

        //sleep for checking the result
        Thread.sleep(10000);
        closer.closeConsumer();

        client.close();
    }  
    
```


[1]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/message-inherits.png
[2]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/carry-inherits.png
[3]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/message-formatter-inherits.png
[4]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/handle-chain.png
[5]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/handler-chain-structure.png
[6]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/handler-chain-config.png
[7]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/message-context.png
[8]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/channel-pool.png
[9]:https://raw.githubusercontent.com/yanghua/messagebus/master/screenshots/zookeeper-node.png