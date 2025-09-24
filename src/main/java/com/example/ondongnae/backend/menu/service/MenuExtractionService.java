package com.example.ondongnae.backend.menu.service;

import com.example.ondongnae.backend.global.exception.BaseException;
import com.example.ondongnae.backend.global.exception.ErrorCode;
import com.example.ondongnae.backend.member.service.AuthService;
import com.example.ondongnae.backend.menu.dto.OcrExtractResponse;
import com.example.ondongnae.backend.menu.ocr.ClovaOcrClient;
import com.example.ondongnae.backend.menu.ocr.ClovaOcrResponse;
import com.example.ondongnae.backend.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuExtractionService {

    private final AuthService authService;
    private final StoreRepository storeRepository;
    private final ClovaOcrClient clovaOcrClient;
    private final MenuTextParser parser;

    public OcrExtractResponse extract(MultipartFile image) {
        Long storeId = authService.getMyStoreId();

        storeRepository.findById(storeId)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_NOT_FOUND));

        if (image == null || image.isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "이미지 파일이 비었습니다.");
        }

        var resp = clovaOcrClient.callOcr(image);
        var text = joinInferTexts(resp); // 좌표 기반 줄 재구성으로 변경

        var items = parser.parse(text);
        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE, "메뉴 텍스트를 찾지 못했습니다.");
        }
        return new OcrExtractResponse(storeId, items);
    }

    // 좌표 기반 행 구성: 세로 중심(cy)로 버킷팅, 행 내부는 x 오름차순
    private String joinInferTexts(ClovaOcrResponse resp) {
        if (resp.getImages() == null || resp.getImages().isEmpty()) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "CLOVA 응답에 images가 없습니다.");
        }
        var img = resp.getImages().get(0);
        if (!"SUCCESS".equalsIgnoreCase(img.getInferResult())) {
            throw new BaseException(ErrorCode.EXTERNAL_API_ERROR, "CLOVA 인식 실패: " + img.getMessage());
        }
        var fields = img.getFields();
        if (fields == null || fields.isEmpty()) return "";

        record Node(String text, double cx, double cy) {}

        List<Node> nodes = new ArrayList<>();
        for (var f : fields) {
            if (f.getInferText() == null || f.getBoundingPoly() == null || f.getBoundingPoly().getVertices() == null) {
                continue;
            }
            var vs = f.getBoundingPoly().getVertices();
            if (vs.isEmpty()) continue;

            double cx = vs.stream().mapToDouble(v -> v.getX() == null ? 0.0 : v.getX()).average().orElse(0.0);
            double cy = vs.stream().mapToDouble(v -> v.getY() == null ? 0.0 : v.getY()).average().orElse(0.0);
            nodes.add(new Node(f.getInferText(), cx, cy));
        }
        if (nodes.isEmpty()) return "";

        // 세로(y) 기준 정렬
        nodes.sort(Comparator.comparingDouble(Node::cy));

        // y-근접 버킷팅
        final double Y_EPS = 18;
        List<List<Node>> rows = new ArrayList<>();
        List<Node> cur = new ArrayList<>();
        double lastCy = -1e9;

        for (var n : nodes) {
            if (cur.isEmpty() || Math.abs(n.cy() - lastCy) <= Y_EPS) {
                cur.add(n);
            } else {
                rows.add(cur);
                cur = new ArrayList<>();
                cur.add(n);
            }
            lastCy = n.cy();
        }
        if (!cur.isEmpty()) rows.add(cur);

        // 각 행 내부는 x 기준 정렬 후 공백으로 이어붙이기
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            row.sort(Comparator.comparingDouble(Node::cx));
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) sb.append(' ');
                sb.append(row.get(j).text());
            }
            if (i < rows.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }
}