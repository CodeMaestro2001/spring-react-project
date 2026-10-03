package com.example.demo.repo;


import com.example.demo.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface ItemRepo extends JpaRepository<Item,Integer> {

}
