/**
 * Copyright (c) 2015-2018, Michael Yang 杨福海 (fuhai999@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jboot.core.rpc.dubbo;

import com.alibaba.dubbo.config.*;
import io.jboot.Jboot;
import io.jboot.core.rpc.JbootrpcBase;
import io.jboot.core.rpc.JbootrpcServiceConfig;
import io.jboot.exception.JbootIllegalConfigException;
import io.jboot.utils.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * dubbo 官方宣布开始维护了
 * 开始添加dubbo的支持
 */
public class JbootDubborpc extends JbootrpcBase {

    private static final Map<String, Object> singletons = new ConcurrentHashMap<>();

    private JbootDubborpcConfig dubboConfig;
    private RegistryConfig registryConfig;

    public JbootDubborpc() {
        dubboConfig = Jboot.config(JbootDubborpcConfig.class);

        registryConfig = new RegistryConfig();
        registryConfig.setCheck(getRpcConfig().isRegistryCheck());

        if (getRpcConfig().getRegistryFile() != null) {
            registryConfig.setFile(getRpcConfig().getRegistryFile());
        }

        /**
         * 注册中心的调用模式
         */
        if (getRpcConfig().isRegistryCallMode()) {

            registryConfig.setProtocol(getRpcConfig().getRegistryType());
            registryConfig.setAddress(getRpcConfig().getRegistryAddress());
            registryConfig.setUsername(getRpcConfig().getRegistryUserName());
            registryConfig.setPassword(getRpcConfig().getRegistryPassword());
        }
        /**
         * 直连模式
         */
        else if (getRpcConfig().isRedirectCallMode()) {
            registryConfig.setAddress(RegistryConfig.NO_AVAILABLE);
        }
    }


    private ApplicationConfig createApplicationConfig(String group) {
        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.setName(group);
        if (dubboConfig.getQosEnable() != null && dubboConfig.getQosEnable()) {
            applicationConfig.setQosEnable(true);
            applicationConfig.setQosPort(dubboConfig.getQosPort());
            applicationConfig.setQosAcceptForeignIp(dubboConfig.getQosAcceptForeignIp());
        } else {
            applicationConfig.setQosEnable(false);
        }

        return applicationConfig;
    }


    @Override
//    public <T> T serviceObtain(Class<T> serviceClass, String group, String version) {
    public <T> T serviceObtain(Class<T> serviceClass, JbootrpcServiceConfig serviceConfig) {

        String key = String.format("%s:%s:%s", serviceClass.getName(), serviceConfig.getGroup(), serviceConfig.getVersion());

        T object = (T) singletons.get(key);
        if (object != null) {
            return object;
        }


        // 注意：ReferenceConfig为重对象，内部封装了与注册中心的连接，以及与服务提供方的连接
        // 引用远程服务
        // 此实例很重，封装了与注册中心的连接以及与提供者的连接，请自行缓存，否则可能造成内存和连接泄漏
        ReferenceConfig<T> reference = new ReferenceConfig<T>();
        reference.setApplication(createApplicationConfig(serviceConfig.getGroup()));
        reference.setInterface(serviceClass);
        reference.setCheck(getRpcConfig().isConsumerCheck());

        initReference(reference, serviceConfig);

        /**
         * 注册中心的调用模式
         */
        if (getRpcConfig().isRegistryCallMode()) {
            reference.setRegistry(registryConfig); // 多个注册中心可以用setRegistries()
        }

        /**
         * 直连调用模式
         */
        else if (getRpcConfig().isRedirectCallMode()) {
            if (StringUtils.isBlank(getRpcConfig().getDirectUrl())) {
                throw new JbootIllegalConfigException("directUrl must not be null if you use redirect call mode，please config jboot.rpc.directUrl value");
            }
            reference.setUrl(getRpcConfig().getDirectUrl());
        }


        // 注意：此代理对象内部封装了所有通讯细节，对象较重，请缓存复用
        object = reference.get();

        if (object != null) {
            singletons.put(key, object);
        }

        return object;
    }


    @Override
//    public <T> boolean serviceExport(Class<T> interfaceClass, Object object, String group, String version, int port) {
    public <T> boolean serviceExport(Class<T> interfaceClass, Object object, JbootrpcServiceConfig serviceConfig) {

        ProtocolConfig protocolConfig = dubboConfig.newProtocolConfig();

        if (protocolConfig.getHost() == null && getRpcConfig().getHost() != null) {
            protocolConfig.setHost(getRpcConfig().getHost());
        }

        if (protocolConfig.getSerialization() == null && getRpcConfig().getSerialization() != null) {
            protocolConfig.setSerialization(getRpcConfig().getSerialization());
        }

        protocolConfig.setPort(serviceConfig.getPort());

        //此实例很重，封装了与注册中心的连接，请自行缓存，否则可能造成内存和连接泄漏
        ServiceConfig<T> service = new ServiceConfig<T>();
        service.setApplication(createApplicationConfig(serviceConfig.getGroup()));
        service.setRegistry(registryConfig); // 多个注册中心可以用setRegistries()
        service.setProtocol(protocolConfig); // 多个协议可以用setProtocols()
        service.setInterface(interfaceClass);
        service.setRef((T) object);

        initService(service, serviceConfig);


        // 暴露及注册服务
        service.export();

        return true;
    }


    private static void initReference(ReferenceConfig reference, JbootrpcServiceConfig config) {
        reference.setGroup(config.getGroup());
        reference.setVersion(config.getVersion());
        reference.setTimeout(config.getTimeout());

        if (config.getRetries() != null) {
            reference.setRetries(config.getRetries());
        }

        if (config.getActives() != null) {
            reference.setActives(config.getActives());
        }

        if (config.getLoadbalance() != null) {
            reference.setLoadbalance(config.getLoadbalance());
        }

        if (config.getAsync() != null) {
            reference.setAsync(config.getAsync());
        }

        if (config.getCheck() != null) {
            reference.setCheck(config.getCheck());
        }


        if (StringUtils.isNotBlank(config.getProxy())) {
            reference.setProxy(config.getProxy());
        } else {
            //默认情况下用于 hystrix 代理
            reference.setProxy("jboot");
        }


        if (StringUtils.isNotBlank(config.getFilter())) {
            reference.setFilter(config.getFilter());
        } else {
            //默认情况下用于 OpenTracing 的追踪
            reference.setFilter("jbootConsumerOpentracing");
        }
    }


    private static void initService(ServiceConfig service, JbootrpcServiceConfig config) {

        service.setGroup(config.getGroup());
        service.setVersion(config.getVersion());
        service.setTimeout(config.getTimeout());

        if (config.getRetries() != null) {
            service.setRetries(config.getRetries());
        }

        if (config.getActives() != null) {
            service.setActives(config.getActives());
        }

        if (config.getLoadbalance() != null) {
            service.setLoadbalance(config.getLoadbalance());
        }

        if (config.getAsync() != null) {
            service.setAsync(config.getAsync());
        }


        if (StringUtils.isNotBlank(config.getProxy())) {
            service.setProxy(config.getProxy());
        } else {
            //默认情况下用于 hystrix 代理
            service.setProxy("jboot");
        }


        if (StringUtils.isNotBlank(config.getFilter())) {
            service.setFilter(config.getFilter());
        } else {
            //默认情况下用于 OpenTracing 的追踪
            service.setFilter("jbootProviderOpentracing");
        }
    }


}
