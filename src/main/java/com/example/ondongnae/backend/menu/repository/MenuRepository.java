package com.example.ondongnae.backend.menu.repository;

import com.example.ondongnae.backend.menu.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    // 메뉴와 알레르기만 별도 로딩
    @Query("""
        select distinct m from Menu m
          left join fetch m.menuAllergies ma
          left join fetch ma.allergy a
        where m.store.id = :storeId
    """)
    List<Menu> findWithAllergiesByStoreId(@Param("storeId") Long storeId);

    List<Menu> findByStoreId(Long storeId);

    @Modifying
    @Query("delete from Menu m where m.store.id = :storeId and m.id in :ids")
    int deleteByStoreIdAndIds(@Param("storeId") Long storeId, @Param("ids") List<Long> ids);
}
