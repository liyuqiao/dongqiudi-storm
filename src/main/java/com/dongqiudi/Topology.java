package com.dongqiudi;

import org.apache.commons.lang.StringUtils;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.hdfs.bolt.HdfsBolt;
import org.apache.storm.hdfs.bolt.format.DefaultFileNameFormat;
import org.apache.storm.hdfs.bolt.format.DelimitedRecordFormat;
import org.apache.storm.hdfs.bolt.format.FileNameFormat;
import org.apache.storm.hdfs.bolt.format.RecordFormat;
import org.apache.storm.hdfs.bolt.rotation.FileRotationPolicy;
import org.apache.storm.hdfs.bolt.rotation.FileSizeRotationPolicy;
import org.apache.storm.hdfs.bolt.sync.CountSyncPolicy;
import org.apache.storm.hdfs.bolt.sync.SyncPolicy;
import org.apache.storm.kafka.*;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.TopologyBuilder;

import java.util.*;

/**
 * Created by Joshua on 16/11/20.
 */
public abstract class Topology {

    protected String name;

    protected TopologyBuilder topologyBuilder;

    protected Config config;

    protected KafkaSpout kafkaSpout;

    protected HdfsBolt hdfsBolt;

    protected List<String> nimbusSeeds;

    protected int workNum;

    public Topology(Properties properties) {

        name = properties.getProperty("task_name");

        topologyBuilder = new TopologyBuilder();

        config = new Config();

        //set nimbus seed
        nimbusSeeds = new ArrayList<String>();
        for (String nimbus : StringUtils.split(properties.getProperty("nimbus_seeds"), ",")) {
            nimbusSeeds.add(nimbus);
        }
        config.put(Config.NIMBUS_SEEDS, nimbusSeeds);


        //set work numbers
        workNum = Integer.valueOf(properties.getProperty("work_num"));
        config.setNumWorkers(workNum);

        //hbase config
        Map<String, String> hbaseConfig = new HashMap<String, String>();
        hbaseConfig.put("zk_hosts", properties.getProperty("zk_hosts_port"));
        hbaseConfig.put("zk_port", properties.getProperty("zk_port"));
        hbaseConfig.put("hmaster", properties.getProperty("hmaster"));
        config.put("hbaseConfig", hbaseConfig);

        initSpoutBolt(properties);
    }

    protected KafkaSpout initKafkaSpout(Properties properties) {
        BrokerHosts hosts = new ZkHosts(properties.getProperty("zk_hosts_port"));
        String kafkaTopic = properties.getProperty("kafka_topic");
        String zkRoot = properties.getProperty("zk_root");
        String taskName = properties.getProperty("task_name");
        SpoutConfig spoutConfig = new SpoutConfig(hosts, kafkaTopic, zkRoot, taskName);
        spoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        return new KafkaSpout(spoutConfig);
    }

    protected HdfsBolt initHdfsBolt(Properties properties) {

        FileNameFormat fileNameFormat = new DefaultFileNameFormat().withPath(properties.getProperty("hdfs_work_dir"));

        RecordFormat format = new DelimitedRecordFormat().withFieldDelimiter(properties.getProperty(","));

        SyncPolicy syncPolicy = new CountSyncPolicy(4096);

        FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(1024.0f, FileSizeRotationPolicy.Units.MB);

        return new HdfsBolt()
                .withFsUrl(properties.getProperty("hdfs_url"))
                .withFileNameFormat(fileNameFormat)
                .withRecordFormat(format)
                .withRotationPolicy(rotationPolicy)
                .withSyncPolicy(syncPolicy);
    }

    public void submit(String mode) {
        if (StringUtils.equals("local", mode)) {
            LocalCluster localCluster = new LocalCluster();
            localCluster.submitTopology(name, config, topologyBuilder.createTopology());
        }
        if (StringUtils.equals("cluster", mode)) {
            try {
                StormSubmitter.submitTopologyWithProgressBar(name, config, topologyBuilder.createTopology());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    protected abstract void initSpoutBolt(Properties properties);


}
