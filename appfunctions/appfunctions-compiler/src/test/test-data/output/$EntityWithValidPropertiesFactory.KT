package EntityWithValidProperties

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.`internal`.AppFunctionSerializableFactory
import com.testdata.EntityWithValidProperties
import javax.`annotation`.processing.Generated

@Generated("androidx.appfunctions.compiler.AppFunctionCompiler")
public class EntityWithValidPropertiesFactory :
    AppFunctionSerializableFactory<EntityWithValidProperties> {
  override fun fromAppFunctionData(appFunctionData: AppFunctionData): EntityWithValidProperties {
    val longParam = appFunctionData.getLong("longParam")
    val doubleParam = appFunctionData.getDouble("doubleParam")
    val boolParam = appFunctionData.getBoolean("boolParam")
    val stringParam = appFunctionData.getString("stringParam")
    val longArray = appFunctionData.getLongArray("longArray") ?: LongArray(0)
    val doubleArray = appFunctionData.getDoubleArray("doubleArray") ?: DoubleArray(0)
    val boolArray = appFunctionData.getBooleanArray("boolArray") ?: BooleanArray(0)
    val stringList = appFunctionData.getStringList("stringList") ?: emptyList()

    return EntityWithValidProperties(longParam, doubleParam, boolParam, stringParam, longArray,
        doubleArray, boolArray, stringList)
  }

  override fun toAppFunctionData(entityWithValidProperties: EntityWithValidProperties):
      AppFunctionData {
    val builder = AppFunctionData.Builder("")

    builder.setLong("longParam", entityWithValidProperties.longParam)
    builder.setDouble("doubleParam", entityWithValidProperties.doubleParam)
    builder.setBoolean("boolParam", entityWithValidProperties.boolParam)
    builder.setString("stringParam", entityWithValidProperties.stringParam)
    builder.setLongArray("longArray", entityWithValidProperties.longArray)
    builder.setDoubleArray("doubleArray", entityWithValidProperties.doubleArray)
    builder.setBooleanArray("boolArray", entityWithValidProperties.boolArray)
    builder.setStringList("stringList", entityWithValidProperties.stringList)

    return builder.build()
  }
}
