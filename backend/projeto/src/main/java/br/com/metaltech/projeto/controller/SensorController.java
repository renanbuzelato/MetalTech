package br.com.metaltech.projeto.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("sensor")

public class SensorController {
    @GetMapping
    public String sensor(){
        return "Olá Spring!";
    }    
}
