package com.guner.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guner.weather.dto.WeatherDto;
import com.guner.weather.dto.WeatherResponse;
import com.guner.weather.model.WeatherEntity;
import com.guner.weather.repository.WeatherRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static com.guner.weather.constants.Constants.*;

@Service
@CacheConfig(cacheNames = {"weathers"})
public class WeatherService {

    private final static Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private final WeatherRepository weatherRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherService(WeatherRepository weatherRepository, RestTemplate restTemplate) {
        this.weatherRepository = weatherRepository;
        this.restTemplate = restTemplate;
    }

    @Cacheable(key = "#city")
    public WeatherDto getWeatherByCityName(String city){

        logger.info("Requested city" + city);
        Optional<WeatherEntity> weatherEntityOptional = weatherRepository.findFirstByRequestedCityNameOrderByUpdatedTimeDesc(city);

        return weatherEntityOptional.map(weather->{
            if(weather.getUpdatedTime().isBefore(LocalDateTime.now().minusMinutes(30))){
                return WeatherDto.convert(getWeatherFromWeatherStack(city));
            }
            return WeatherDto.convert(weather);
        }).orElseGet(()-> WeatherDto.convert(getWeatherFromWeatherStack(city)));
    }

    private WeatherEntity getWeatherFromWeatherStack(String city){
        String url = getWeatherStackUrl(city);
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);

        try {
            WeatherResponse weatherResponse = objectMapper.readValue(responseEntity.getBody(), WeatherResponse.class);
            return saveWeatherEntity(city,weatherResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @CacheEvict(allEntries = true)
    @PostConstruct
    @Scheduled(fixedRateString = "10000")
    public void clearCache(){
        logger.info("Cache cleared");
    }

    private WeatherEntity saveWeatherEntity(String city, WeatherResponse weatherResponse){
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        WeatherEntity weatherEntity = new WeatherEntity(city,
                weatherResponse.location().name(),
                weatherResponse.location().country(),weatherResponse.current().temperature(),
                LocalDateTime.now(),
                LocalDateTime.parse(weatherResponse.location().localtime(),dateTimeFormatter));

        return weatherRepository.save(weatherEntity);
    }

    private String getWeatherStackUrl(String city){
        return WEATHER_STACK_API_BASE_URL + WEATHER_STACK_API_ACCESS_KEY_PARAM + API_KEY + WEATHER_STACK_API_QUERY_PARAM + city;
    }
}
