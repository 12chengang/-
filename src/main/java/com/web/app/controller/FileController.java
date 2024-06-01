package com.web.app.controller;

import cn.hutool.core.io.FileUtil;
import com.web.app.common.BaseResponse;
import com.web.app.common.ErrorCode;
import com.web.app.common.ResultUtils;
import com.web.app.exception.BusinessException;
import com.web.app.exception.ThrowUtils;
import com.web.app.model.entity.User;
import com.web.app.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件接口
 */
@RestController
@Component
@RequestMapping("/file")
@Slf4j
public class FileController {


    @Value("${host:localhost}")
    private String host;

    @Value("${server.port:8080}")
    private String port;

    @Resource
    private UserService userService;

    private final String uploadDirectory = System.getProperty("user.dir") + File.separator + "files";

    /**
     * 视频类型
     */
    private final List<String> videoTypeList = Arrays.asList("mp4", "avi", "mov", "wmv", "flv", "mkv", "rmvb", "rm", "3gp", "dat", "ts", "mts", "vob");

    /**
     * 图片类型
     */
    private final List<String> imageTypeList = Arrays.asList("jpeg", "jpg", "svg", "png", "webp");

    /**
     * 音频类型
     */
    private final List<String> audioTypeList = Arrays.asList("mp3", "wav", "wma", "ogg", "ape", "acc", "flac");

    /**
     * 文档类型
     */
    private final List<String> documentTypeList = Arrays.asList("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", "md");


    /**
     * 校验文件
     *
     * @param multipartFile 文件
     */
    private void validFile(MultipartFile multipartFile) {

        // 把上面各种类型的文件后缀都放到一个集合里面
        List<String> fileSuffixList = new ArrayList<>();
        fileSuffixList.addAll(videoTypeList);
        fileSuffixList.addAll(imageTypeList);
        fileSuffixList.addAll(audioTypeList);
        fileSuffixList.addAll(documentTypeList);

        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 10 * 1024 * 1024L;
        if (fileSize > ONE_M) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 10M");
        }
        if (!fileSuffixList.contains(fileSuffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
        }
    }


    /**
     * 文件上传
     *
     * @param file    文件
     * @param request 请求
     * @return 文件路径
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile file, HttpServletRequest request) {

        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR, "用户未登录");

        // 校验文件是否未空
        ThrowUtils.throwIf(file == null, ErrorCode.PARAMS_ERROR, "文件不能为空");

        // 校验文件
        validFile(file);

        String originalFilename = file.getOriginalFilename();
        String mainName = FileUtil.mainName(originalFilename);
        String extName = FileUtil.extName(originalFilename);

        String rootPath = getRootPath(extName);
        String filePath = rootPath + File.separator + mainName + "." + extName;

        File saveFile = new File(filePath);

        if (!saveFile.getParentFile().exists()) {
            FileUtil.mkdir(saveFile.getParentFile());
        }

        if (saveFile.exists()) {
            saveFile = new File(rootPath + File.separator + mainName + "_" + System.currentTimeMillis() + "." + extName);
        }

        try {
            file.transferTo(saveFile);
            String fileUrl = getFileUrl(extName, saveFile);
            log.info("文件上传成功，文件路径：{}", fileUrl);
            return ResultUtils.success(fileUrl);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件上传失败");
        }
    }

    @NotNull
    private String getFileUrl(String extName, File saveFile) {
        String rootUrl = "http://" + host + ":" + port + "/api/file/download/"; // 文件接口访问路径

        if (videoTypeList.contains(extName)) {
            rootUrl += "video/";
        } else if (imageTypeList.contains(extName)) {
            rootUrl += "image/";
        } else if (audioTypeList.contains(extName)) {
            rootUrl += "audio/";
        } else if (documentTypeList.contains(extName)) {
            rootUrl += "document/";
        } else {
            rootUrl += "other/";
        }
        return rootUrl + saveFile.getName();
    }

    @NotNull
    private String getRootPath(String extName) {
        String rootPath = uploadDirectory;
        if (videoTypeList.contains(extName)) {
            rootPath += File.separator + "video";
        } else if (imageTypeList.contains(extName)) {
            rootPath += File.separator + "image";
        } else if (audioTypeList.contains(extName)) {
            rootPath += File.separator + "audio";
        } else if (documentTypeList.contains(extName)) {
            rootPath += File.separator + "document";
        } else {
            rootPath += File.separator + "other";
        }
        return rootPath;
    }

    /**
     * 文件下载
     *
     * @param fileName 文件名称
     * @return true
     */
    @GetMapping("/download/{fileType}/{fileName}")
    public BaseResponse<Boolean> downloadFile(@PathVariable("fileType") String fileType, @PathVariable("fileName") String fileName, HttpServletResponse response, HttpServletRequest request) throws IOException {

        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR, "用户未登录");

        String filePath = uploadDirectory + File.separator + fileType + File.separator + fileName;
        ThrowUtils.throwIf(!FileUtil.exist(filePath), ErrorCode.PARAMS_ERROR, "文件不存在");

        byte[] bytes = FileUtil.readBytes(filePath);

        try {
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
            return ResultUtils.success(true);
        } catch (IOException e) {
            log.error("文件下载失败", e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件下载失败");
        }

    }
}
