package com.example.demo.controller;

import com.example.demo.dto.ItemDto;
import com.example.demo.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping(value = "api/")

public class ItemController {

    @Autowired
    private ItemService itemService;

    @GetMapping("/items")

    public List<ItemDto> items(){
        return itemService.getAllItems();
    }
}
