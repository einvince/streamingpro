package streaming.core

import java.io.File

import net.sf.json.JSONObject
import org.apache.spark.sql.{Row, SaveMode, functions => F}
import org.apache.spark.sql.types._
import org.apache.spark.streaming.BasicSparkOperation
import streaming.core.strategy.platform.SparkRuntime
import org.apache.spark.ml.linalg.{Vector, Vectors}
import streaming.dsl.ScriptSQLExec
import streaming.dsl.mmlib.algs.SQLAutoFeature
import streaming.dsl.mmlib.algs.feature.{DiscretizerIntFeature, HighOrdinalDoubleFeature, StringFeature}


/**
  * Created by allwefantasy on 6/5/2018.
  */
class AutoMLSpec extends BasicSparkOperation with SpecFunctions with BasicMLSQLConfig {

  copySampleLibsvmData

  "tfidf featurize" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val dataRDD = spark.sparkContext.parallelize(Seq("我是天才，你呢", "你真的很棒", "天才你好")).map { f =>
        Row.fromSeq(Seq(f))
      }
      val df = spark.createDataFrame(dataRDD,
        StructType(Seq(StructField("content", StringType))))

      writeStringToFile("/tmp/tfidf/stopwords", List("你").mkString("\n"))
      writeStringToFile("/tmp/tfidf/prioritywords", List("天才").mkString("\n"))

      /*
          真的:0.0
          ，:1.0
          棒:2.0
          天才:3.0
          是:4.0
          我:5.0
          很:6.0
          你好:7.0
          呢:8.0
       */
      var newDF = StringFeature.tfidf(df, "/tmp/tfidf/mapping", "", "content", "/tmp/tfidf/stopwords", "/tmp/tfidf/prioritywords", 100000.0, Seq(), true)
      var res = newDF.collect()
      assume(res.size == 3)
      assume(res(0).getAs[Vector]("content").size == 9)
      var res2 = newDF.collect().filter { f =>
        val v = f.getAs[Vector](f.fieldIndex("content"))
        if (v(3) != 0) {
          assume(v.argmax == 3)
        }
        v(3) != 0
      }
      assume(res2.size == 2)
      println(newDF.toJSON.collect().mkString("\n"))

      newDF = StringFeature.tfidf(df, "/tmp/tfidf/mapping", "", "content", "", null, 100000.0, Seq(), true)
      res = newDF.collect()
      assume(res(0).getAs[Vector]("content").size == 10)

      res2 = newDF.collect().filter { f =>
        val v = f.getAs[Vector](f.fieldIndex("content"))
        v(v.argmax) < 1
      }
      assume(res2.size == 3)


    }
  }

  "tfidf featurize with ngram" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val dataRDD = spark.sparkContext.parallelize(Seq("我是天才，你呢", "你真的很棒", "天才你好")).map { f =>
        Row.fromSeq(Seq(f))
      }
      val df = spark.createDataFrame(dataRDD,
        StructType(Seq(StructField("content", StringType))))

      writeStringToFile("/tmp/tfidf/stopwords", List("你").mkString("\n"))
      writeStringToFile("/tmp/tfidf/prioritywords", List("天才").mkString("\n"))

      /*
          真的:0.0
          我 是 天才:1.0
          我 是:2.0
          你:3.0
          天才 ， 你:4.0
          天才 ，:5.0
          ， 你 呢:6.0
          你 真的 很:7.0
          ，:8.0
          真的 很:9.0
          棒:10.0
          ， 你:11.0
          很 棒:12.0
          是 天才:13.0
          你 呢:14.0
          天才 你好:15.0
          天才:16.0
          是:17.0
          你 真的:18.0
          我:19.0
          很:20.0
          是 天才 ，:21.0
          你好:22.0
          真的 很 棒:23.0
          呢:24.0
       */

      val newDF = StringFeature.tfidf(df, "/tmp/tfidf/mapping", "", "content", "", null, 100000.0, Seq(2, 3), true)
      val res = newDF.collect()
      assume(res(0).getAs[Vector]("content").size == 25)
      newDF.show(false)
    }
  }

  "DiscretizerIntFeature" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      val dataRDD = spark.sparkContext.parallelize(Seq(
        Seq(1, 2, 3),
        Seq(1, 4, 3),
        Seq(1, 7, 3))).map { f =>
        Row.fromSeq(f)
      }
      val df = spark.createDataFrame(dataRDD,
        StructType(Seq(
          StructField("a", IntegerType),
          StructField("b", IntegerType),
          StructField("c", IntegerType)
        )))

      val newDF = DiscretizerIntFeature.vectorize(df, "/tmp/tfidf/mapping", Seq("a", "b", "c"))
      newDF.show(false)
    }
  }

  "HighOrdinalDoubleFeature" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      val dataRDD = spark.sparkContext.parallelize(Seq(
        Seq(1.0, 2.0, 3.0),
        Seq(1.0, 4.0, 3.0),
        Seq(1.0, 7.0, 3.0))).map { f =>
        Row.fromSeq(f)
      }
      val df = spark.createDataFrame(dataRDD,
        StructType(Seq(
          StructField("a", DoubleType),
          StructField("b", DoubleType),
          StructField("c", DoubleType)
        )))

      val newDF = HighOrdinalDoubleFeature.vectorize(df, "/tmp/tfidf/mapping", Seq("a", "b", "c"))
      newDF.show(false)
    }
  }

  "AutoFeature" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession

      writeStringToFile("/tmp/tfidf/stopwords", List("你").mkString("\n"))
      writeStringToFile("/tmp/tfidf/prioritywords", List("天才").mkString("\n"))

      val dataRDD = spark.sparkContext.parallelize(Seq(
        Seq("我是天才，你呢", 1.0, 2.0, 3.0, 1, 2, 3),
        Seq("你真的很棒", 1.0, 4.0, 3.0, 1, 4, 3),
        Seq("天才你好", 1.0, 7.0, 3.0, 1, 7, 3)
      )).map { f =>
        Row.fromSeq(f)
      }
      val df = spark.createDataFrame(dataRDD,
        StructType(Seq(
          StructField("content", StringType),
          StructField("a", DoubleType),
          StructField("b", DoubleType),
          StructField("c", DoubleType),
          StructField("a1", IntegerType),
          StructField("b1", IntegerType),
          StructField("c1", IntegerType)

        )))
      val af = new SQLAutoFeature()
      af.train(df, "/tmp/automl", Map(
        "mappingPath" -> "/tmp/tfidf/mapping",
        "textFileds" -> "content",
        "priorityDicPath" -> "/tmp/tfidf/stopwords",
        "stopWordPath" -> "/tmp/tfidf/prioritywords",
        "priority" -> "3",
        "nGrams" -> "2,3"
      ))
      val ml = spark.read.parquet("/tmp/automl")
      ml.show(false)

    }
  }

  "test" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      import org.apache.spark.ml.feature.NGram

      import org.apache.spark.ml.feature.PCA
      import org.apache.spark.ml.linalg.Vectors

      val data = Array(
        Vectors.sparse(5, Seq((1, 1.0), (3, 7.0))),
        Vectors.dense(2.0, 0.0, 3.0, 4.0, 5.0),
        Vectors.dense(4.0, 0.0, 0.0, 6.0, 7.0)
      )
      val df = spark.createDataFrame(data.map(Tuple1.apply)).toDF("features")

      val pca = new PCA()
        .setInputCol("features")
        .setOutputCol("pcaFeatures")
        .setK(3)
        .fit(df)

      val result = pca.transform(df).select("pcaFeatures")
      result.show(false)

    }
  }

  "word2vec featurize" should "work fine" in {

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val dataRDD = spark.sparkContext.parallelize(Seq("我是天才，你呢", "你真的很棒", "天才你好")).map { f =>
        Row.fromSeq(Seq(f))
      }
      val df = spark.createDataFrame(dataRDD,
        StructType(Seq(StructField("content", StringType))))
      val newDF = StringFeature.word2vec(df, "/tmp/word2vec/mapping", "", "content", null)
      println(newDF.toJSON.collect().mkString("\n"))

    }
  }

  def copySampleLibsvmData = {
    writeStringToFile("/tmp/william/sample_libsvm_data.txt", loadDataStr("sample_libsvm_data.txt"))
  }

  "sklearn-multi-model" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("sklearn-multi-model-trainning"), sq)
      spark.read.parquet("/tmp/william/tmp/model/0").show()
    }
  }

  "sklearn-multi-model-with-sample" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("sklearn-multi-model-trainning-with-sample"), sq)
      spark.read.parquet("/tmp/william/tmp/model/0").show()
    }
  }

  "tensorflow-cnn-model" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("tensorflow-cnn"), sq)

    }
  }

  "SQLSampler" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("sql-sampler"), sq)
      var df = spark.sql("select count(*) as num,__split__ as rate from sample_data group by __split__ ")
      assume(df.count() == 3)
      df = spark.sql("select label,__split__,count(__split__) as rate from sample_data  group by label,__split__ order by label,__split__,rate")
      df.show(10000)

    }
  }

  "SQLTfIdfInPlace" should "work fine" in {
    withBatchContext(setupBatchContext(batchParams, "classpath:///test/empty.json")) { runtime: SparkRuntime =>
      //执行sql
      implicit val spark = runtime.sparkSession
      val dataRDD = spark.sparkContext.parallelize(Seq("我是天才，你呢", "你真的很棒", "天才你好")).map { f =>
        Row.fromSeq(Seq(f))
      }
      val df = spark.createDataFrame(dataRDD,
        StructType(Seq(StructField("content", StringType))))

      df.write.mode(SaveMode.Overwrite).parquet("/tmp/william/tmp/tfidf/df")

      writeStringToFile("/tmp/tfidf/stopwords", List("你").mkString("\n"))
      writeStringToFile("/tmp/tfidf/prioritywords", List("天才").mkString("\n"))

      val sq = createSSEL
      ScriptSQLExec.parse(loadSQLScriptStr("tfidfplace"), sq)
      // we should make sure train vector and predict vector the same
      val trainVector = spark.sql("select * from parquet.`/tmp/william/tmp/tfidfinplace/data`").toJSON.collect()
      val predictVector = spark.sql("select jack(content) as content from orginal_text_corpus").toJSON.collect()
      predictVector.foreach { f =>
        println(f)
        assume(trainVector.contains(f))
      }

    }
  }
}
