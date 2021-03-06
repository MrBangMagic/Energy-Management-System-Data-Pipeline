package batch
package batch

import org.apache.spark.{SparkConf, SparkContext}
import utils.SparkUtils.{getSQLContext, getSparkContext}
import org.apache.spark.ml.feature.{OneHotEncoder, StringIndexer}
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.functions._
import org.apache.spark.sql.hive.HiveContext
import utils.SparkUtils



object bathc_job_II extends App {



    val sc = getSparkContext("forecasted")
    val sqlContext = getSQLContext(sc)
    val hc = new HiveContext(sc)

    val tmp_4 = hc.read.parquet("/home/bju/data_app/data_EM/data_2/").cache()
  //val tmp_4 = hc.read.parquet(config.Settings.HHLogGen.hdfsPath).cache()
    tmp_4.select("inst").distinct.show(20)
    val tmp_bb = tmp_4.select("inst").distinct.collect.flatMap(_.toSeq)

    tmp_bb.foreach(blk => {

      val cons = consum_mods.Cons_12HFor("2018-06-06:06", blk.toString, tmp_4.where(tmp_4("inst") === blk.toString).toDF())
      val conf = new SparkConf()
        .setAppName("forecast_2")
        .setMaster("local[*]")
        .set("spark.casandra.connection.host", "localhost")
      val sc2 = SparkContext.getOrCreate(conf)
      val sqlContext2 = getSQLContext(sc2)

      val millis: Long = System.currentTimeMillis
      val hour = millis / (1000 * 60 * 60)
      val model = utils.SparkUtils.update_model(sc2)

      import sqlContext.implicits._

      import sqlContext2.implicits._
      //Se construye desde el historico de cobertura de nubes por horas frente a generación
      val path = "/home/bju/data_app/sample_gen.json"
      val df = sqlContext2.read.json(path)
      //df.show(40)
      import sqlContext2.implicits._
      val df_tmp_3 = df.map(r => {
        var gh = for (i <- 0 until 23) yield {
          Logs.STF_Hour(i.toString, r.getAs[String]("H" + i))

        }
        gh
      })

      //cogemos solo cinco instalaciones, aqui vinculariamos con la previsión de todas las instalaciones
      df_tmp_3.take(1).foreach(d => {
        import sqlContext2.implicits._
        var df_tmp_b_2_c = d.toDF().selectExpr("cast(hora as double) hora",
          "nub"
        )
        var df_4 = model.transform(df_tmp_b_2_c)
        var df_5 = df_4.withColumn("pred_norm", when($"prediction" < 0, 0).otherwise($"prediction"))
        var gen_h = df_5.select("hora", "pred_norm").map(r => (r.getAs[Double]("hora"), r.getAs[Double]("pred_norm"))).map(a => Logs.UG_Hour(a._1, a._2)) //.map(r => Map(df.columns.zip(r.toSeq):_*))//

        val fn = Estimates.run.HourFordward(blk.toString, gen_h, cons)
        val pers = fn.estados.map(a => Logs.FRD_HH_2CSR(a._1, a._2, a._3, a._4, a._5.bat_acc_t, a._5.excess, a._5.bat, a._5.pool)).toDF()
        pers
          .write
          .format("org.apache.spark.sql.cassandra")
          .options(Map("keyspace" -> "em", "table" -> "inst_frwd"))
          .mode(SaveMode.Append)
          .save()

      })
    })


  }


