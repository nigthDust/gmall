package com.atguigu.gmall.ums.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 关注活动表
 * 
 * @author lyc
 * @email lyc@atguigu.com
 * @date 2023-08-10 09:50:53
 */
@Data
@TableName("ums_user_collect_subject")
public class UserCollectSubjectEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * 用户id
	 */
	private Integer userId;
	/**
	 * 活动id
	 */
	private Long subjectId;
	/**
	 * 活动名称
	 */
	private String subjectName;
	/**
	 * 活动默认图片
	 */
	private String subjectImage;
	/**
	 * 活动链接
	 */
	private String subjectUrl;
	/**
	 * 关注时间
	 */
	private Date createTime;

}
