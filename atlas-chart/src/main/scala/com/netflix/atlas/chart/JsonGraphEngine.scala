/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.atlas.chart

import java.io.OutputStream
import java.io.OutputStreamWriter

class JsonGraphEngine extends GraphEngine {

  import com.netflix.atlas.chart.GraphEngine._

  val contentType: String = "application/json"

  def write(config: GraphDef, output: OutputStream) {
    val writer = new OutputStreamWriter(output, "UTF-8")
    val seriesList = config.plots.flatMap(_.series)
    val count = seriesList.size
    val numberFmt = config.numberFormat
    val gen = jsonFactory.createGenerator(writer)

    gen.writeStartObject()
    gen.writeNumberField("start", config.startTime.toEpochMilli)
    gen.writeNumberField("step", config.step)

    gen.writeArrayFieldStart("legend")
    (0 until count).zip(seriesList).foreach {
      case (i, series) =>
        val label = series.label
        gen.writeString(label)
    }
    gen.writeEndArray()

    gen.writeArrayFieldStart("metrics")
    seriesList.foreach { series =>
      gen.writeStartObject()
      series.tags.toList.sortWith(_._1 < _._1).foreach { t =>
        gen.writeStringField(t._1, t._2)
      }
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeArrayFieldStart("values")
    val step = config.step.asInstanceOf[Int]
    val endTime = config.endTime.toEpochMilli
    var timestamp = config.startTime.toEpochMilli
    while (timestamp <= endTime) {
      gen.writeStartArray()
      seriesList.foreach { series =>
        val v = series.data.data(timestamp)
        gen.writeRawValue(numberFmt.format(v))
      }
      gen.writeEndArray()
      timestamp += step
    }
    gen.writeEndArray()

    gen.writeArrayFieldStart("notices")
    config.notices.foreach {
      case Info(msg)    => gen.writeString("INFO: " + msg)
      case Warning(msg) => gen.writeString("WARNING: " + msg)
      case Error(msg)   => gen.writeString("ERROR: " + msg)
    }
    gen.writeEndArray()

    if (config.stats.inputLines > 0) {
      val start = config.startTime.toEpochMilli / 1000
      val end = config.endTime.toEpochMilli / 1000
      val graphLines = config.plots.map(_.series.size).sum
      val graphDatapoints = graphLines * ((end - start) / (config.step / 1000) + 1)

      gen.writeObjectFieldStart("explain")
      gen.writeNumberField("dataFetchTime", config.loadTime)

      gen.writeNumberField("inputLines", config.stats.inputLines)
      gen.writeNumberField("intermediateLines", config.stats.outputLines)
      gen.writeNumberField("graphLines", graphLines)

      gen.writeNumberField("inputDatapoints", config.stats.inputDatapoints)
      gen.writeNumberField("intermediateDatapoints", config.stats.outputDatapoints)
      gen.writeNumberField("graphDatapoints", graphDatapoints)
      gen.writeEndObject()
    }

    gen.writeEndObject()
    gen.flush()
  }
}
