package fi.hsl.gtfsrt2hfp.utils

import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.commons.configuration2.ImmutableConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler
import org.apache.commons.configuration2.io.FileHandler
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object ConfigLoader {
    private fun loadConfig(inputStream: InputStream): ImmutableConfiguration {
        val parameters = Parameters()
        val fileBasedConfiguration = FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration::class.java)
            .configure(parameters.properties()
                .setListDelimiterHandler(DefaultListDelimiterHandler(','))
            )
            .configuration

        val fileHandler = FileHandler(fileBasedConfiguration)
        fileHandler.load(inputStream, StandardCharsets.UTF_8.name())

        return fileBasedConfiguration
    }

    fun loadConfigFromResource(resourceName: String): ImmutableConfiguration {
        return ConfigLoader::class.java.classLoader.getResourceAsStream(resourceName).use {
            loadConfig(it)
        }
    }

    fun loadConfigFromFile(path: Path): ImmutableConfiguration {
        return Files.newInputStream(path).use {
            loadConfig(it)
        }
    }
}