package com.hortonworks.spark.sql.kafka08

import java.util.Properties

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

/**
  * Created by allwefantasy on 23/4/2018.
  */
object KafkaOperator {

  def filterScore(str: String) = {
    if (str.startsWith("mlsql_validation_score:")) {
      str.split(":").last.toDouble
    } else 0d
  }

  def writeKafka(prefix: String, kafkaParam: Map[String, String], lines: Iterator[String]) = {

    if (!kafkaParam.contains("userName")) {
      lines.map(f => filterScore(f)).filter(f => f > 0d).toSeq
    } else {
      val topic = kafkaParam("userName") + "_training_msg"

      val props = new Properties()
      kafkaParam.foreach(f => props.put(f._1, f._2))


      props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
      props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
      val producer = new KafkaProducer[String, String](props)
      try {
        lines.map { line =>
          producer.send(new ProducerRecord[String, String](topic, prefix + "" + line))
          filterScore(line)
        }.filter(f => f > 0d).toSeq
      } finally {
        producer.close()
      }
    }
  }

}
