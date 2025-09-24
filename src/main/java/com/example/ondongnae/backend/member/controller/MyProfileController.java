package com.example.ondongnae.backend.member.controller;

import com.example.ondongnae.backend.global.response.ApiResponse;
import com.example.ondongnae.backend.member.dto.MyProfileResponse;
import com.example.ondongnae.backend.member.dto.MyProfileUpdateRequest;
import com.example.ondongnae.backend.member.service.MemberService;
import com.example.ondongnae.backend.store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/me/profile")
public class MyProfileController {

    private final StoreService storeService;
    private final MemberService memberService;

    // 내 정보 조회
    @GetMapping
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile() {
        var data = storeService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.ok("내 정보 조회 성공", data));
    }

    // 내 정보 수정
    @PatchMapping
    public ResponseEntity<ApiResponse<Void>> updateMyProfile(
            @Valid @RequestBody MyProfileUpdateRequest req) {
        storeService.updateMyProfile(req);
        return ResponseEntity.ok(ApiResponse.ok("수정 완료", null));
    }

    // 탈퇴
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteMyProfile() {
        memberService.deleteMember();
        return ResponseEntity.ok(ApiResponse.ok("삭제 완료", null));
    }

}
