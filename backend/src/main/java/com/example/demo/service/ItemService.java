package com.example.demo.service;

import com.example.demo.dto.ItemDto;
import com.example.demo.model.Item;
import com.example.demo.repo.ItemRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class ItemService {
    @Autowired
    private ItemRepo itemRepo;

    @Autowired
    private ModelMapper modelMapper;

    public List<ItemDto> getAllItems(){
        List<Item>itemList= itemRepo.findAll();
        return modelMapper.map(itemList, new TypeToken<List<ItemDto>>(){}.getType());
    }
}
