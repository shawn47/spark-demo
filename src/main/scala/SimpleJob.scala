import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf

// import org.apache.spark.mllib.recommendation.ALS
// import org.apache.spark.mllib.recommendation.Rating

import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors

import org.apache.spark.mllib.classification.LogisticRegressionWithSGD
import org.apache.spark.mllib.classification.SVMWithSGD
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.tree.DecisionTree
import org.apache.spark.mllib.tree.configuration.Algo
import org.apache.spark.mllib.tree.impurity.Entropy

import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.feature.StandardScaler



object SimpleJob {
	val numIterations = 100
	val stepSize = 0.0001
	val maxTreeDepth = 25
	val basePath = "/tmp/titanic"

	val NAME_MR = "Mr."
	val NAME_MISS = "Miss."
	val NAME_MRS = "Mrs."

	// val NAME_DR = "Dr."
	// val NAME_MASTER = "Master."
	// val NAME_MAJOR = "Major."
	// val NAME_LADY = "Lady."
	// val NAME_QUOTE = "\""


	def main(args: Array[String]) {
		val dataFile = "/Users/xiaoyongbo/Documents/projects/spark/Titanic_Disaster/train_noheader_withoutname.csv"
		val conf = new SparkConf().setAppName("Simple Application")
		val sc = new SparkContext(conf)
		val rawData = sc.textFile(dataFile, 2).cache()

		val rawRatings = rawData.map(_.split(","))

		val data = rawRatings.map( r => {
			val trimmed = r.map(_.replaceAll("\"", ""))
			val label = trimmed(r.size - 1).toInt
			val features = trimmed.slice(0, r.size - 1).map( d => {
				if (d == "male") 1.0
				else if (d == "female") 2.0
				else if (d == "S") 1.0
				else if (d == "C") 2.0
				else if (d == "Q") 3.0
				// add Passenger Name Property
				else if (d.contains(NAME_MR)) 1.0
				else if (d.contains(NAME_MISS)) 3.0
				else if (d.contains(NAME_MRS)) 2.0
				else if (d == "") 0.0
				else 0.0
			})
			LabeledPoint(label, Vectors.dense(features))
		})




		data.cache
		val numData = data.count

		val vectors = data.map(lp => lp.features)

		val scaler = new StandardScaler(withMean = true, withStd = true).fit(vectors)
		val scaledData = data.map(lp => LabeledPoint(lp.label, scaler.transform(lp.features)))

		val lrModelScaled = LogisticRegressionWithSGD.train(scaledData, numIterations, stepSize)
		val svmModelScaled = SVMWithSGD.train(scaledData, numIterations)

		val nbModelCats = NaiveBayes.train(data, 1.0, "multinomial")
		val dtModelCats = DecisionTree.train(scaledData, Algo.Classification, Entropy, maxTreeDepth)

		val scaledMetrics = Seq(lrModelScaled, svmModelScaled).map(model => {
			val scoreAndLabels = scaledData.map(point => {
				(model.predict(point.features), point.label)
			})
			val metrics = new BinaryClassificationMetrics(scoreAndLabels)
			(model.getClass.getSimpleName, metrics.areaUnderPR, metrics.areaUnderROC)
		})

		val nbMetrics = Seq(nbModelCats).map{ model =>
			val scoreAndLabels = data.map { point =>
				val score = model.predict(point.features)
				(if (score > 0.5) 1.0 else 0.0, point.label)
			}
			val metrics = new BinaryClassificationMetrics(scoreAndLabels)
			(model.getClass.getSimpleName, metrics.areaUnderPR, metrics.areaUnderROC)
		}

		val dtMetrics = Seq(dtModelCats).map{ model =>
			val scoreAndLabels = scaledData.map { point =>
				val score = model.predict(point.features)
				(if (score > 0.5) 1.0 else 0.0, point.label)
			}
			val metrics = new BinaryClassificationMetrics(scoreAndLabels) 
			(model.getClass.getSimpleName, metrics.areaUnderPR, metrics.areaUnderROC)
		}

		val allMetrics = scaledMetrics ++ nbMetrics ++ dtMetrics
		// val allMetricsOld = allMetrics.foreach{ case (m, pr, roc) =>
		// 	// println(f"$m, Area under PR: ${pr * 100.0}%2.4f%%, Area under ROC: ${roc * 100.0}%2.4f%%")
		// 	Seq(f"$m, Area under PR: ${pr * 100.0}%2.4f%%, Area under ROC: ${roc * 100.0}%2.4f%%")
		// }

		// val testFile = "/Users/xiaoyongbo/Documents/projects/spark/Titanic_Disaster/test_noheader_withoutname.csv"
		// val testData = sc.textFile(testFile, 2).cache()
		// val testRawData = testData.map(_.split(","))
		
		// val preData = testRawData.map( r => {
		// 	val trimmed = r.map(_.replaceAll("\"", ""))
		// 	val label = trimmed(r.size - 1).toInt
		// 	val features = trimmed.slice(0, r.size - 1).map( d => {
		// 		if (d == "male") 1.0
		// 		else if (d == "female") 2.0
		// 		else if (d == "S") 1.0
		// 		else if (d == "C") 2.0
		// 		else if (d == "Q") 3.0
		// 		else if (d == "") 0.0
		// 		else d.toDouble
		// 	})
		// 	LabeledPoint(label, Vectors.dense(features))
		// })

		// val predictions = svmModel.predict(preData.map(test => test.features))

		// val rawResult = preData.zip(predictions)

		// val result = rawResult.map( r => {
		// 	(r._1.features.toArray(0), r._2)
		// })

		

		val allMetricsOld = allMetrics.map{ case (m, pr, roc) => f"$m, Area under PR: ${pr * 100.0}%2.4f%%, Area under ROC: ${roc * 100.0}%2.4f%%" }

		val test_rdd = sc.parallelize(allMetricsOld)

		test_rdd.saveAsTextFile("/Users/xiaoyongbo/Documents/projects/spark/Titanic_Disaster/result_new")
		// allMetricsOld.mkString("\n").saveAsTextFile("/Users/xiaoyongbo/Documents/projects/spark/Titanic_Disaster/result_metrics_old")
	}

	// def trainWithParams(input: RDD[LabeledPoint], regParam: Double, numIterations: Int,
	// 	updater: Updater, stepSize: Double) = {
	// 	val lr = new LogisticRegressionWithSGD
	// 	lr.optimizer.setNumIterations(numIterations).setUpdater(updater).setRegParam(regParam).setStepSize(stepSize)
	// 	lr.run(input)
	// }

	// def createMetrics(label: String, data: RDD[LabeledPoint], model: ClassificationModel) = {
	// 	val scoreAndLabels = data.map { point =>
	// 	    (model.predict(point.features), point.label)
	// 	}
	// 	val metrics = new BinaryClassificationMetrics(scoreAndLabels)
	// 	(label, metrics.areaUnderROC)
	// }
}
/** 
	1  PassengerId
	2  Survived
	3  Pclass
	4  Name
	5  Sex 1 for male, 2 for female
	6  Age
	7  SibSp
	8  Parch
	10 Fare
	12 Embarked 1 for S, 2 for C, 3 for Q
*/