package org.AI.panda.repository;

import org.AI.panda.model.entity.FileSystemNode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileSystemRepository extends MongoRepository<FileSystemNode, String> {
    
    List<FileSystemNode> findByUserIdAndParentId(String userId, String parentId);
    
    Optional<FileSystemNode> findByUserIdAndParentIdAndName(String userId, String parentId, String name);
    
    List<FileSystemNode> findByUserId(String userId);

    // 查找某个目录下的所有子节点 (递归需要应用层处理，或者用 regex 路径，这里简单用 parentId)
    // 如果需要级联删除，可以先查直接子节点
}
