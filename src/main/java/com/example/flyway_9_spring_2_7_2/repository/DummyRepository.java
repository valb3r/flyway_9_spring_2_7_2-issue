package com.example.flyway_9_spring_2_7_2.repository;

import com.example.flyway_9_spring_2_7_2.entity.Dummy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DummyRepository extends JpaRepository<Dummy, Long> {
}
