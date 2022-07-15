package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.utils

import org.apache.commons.configuration2.ConfigurationUtils
import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.commons.configuration2.ImmutableConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler

object ConfigLoader {
    fun loadConfigFromResource(resourceName: String): ImmutableConfiguration {
        val propertiesUrl = ConfigLoader::class.java.classLoader.getResource(resourceName)
        val parameters = Parameters()
        return ConfigurationUtils.unmodifiableConfiguration(
            FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration::class.java)
                .configure(parameters.properties()
                    .setURL(propertiesUrl)
                    .setListDelimiterHandler(DefaultListDelimiterHandler(','))
                )
                .configuration
        )
    }
}