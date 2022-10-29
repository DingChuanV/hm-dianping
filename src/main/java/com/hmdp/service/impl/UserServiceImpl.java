package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.apache.catalina.Session;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.TimeUnit;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;
import static com.hmdp.utils.RedisConstants.*;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * <p>
 * 服务实现类
 * </>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {

        // 1. 判断用户提交的手机号是否正确
        boolean codeInvalid = RegexUtils.isPhoneInvalid(phone);

        // 手机号不合法，退出方法，返回错误信息
        if (codeInvalid) {//手机号格式匹配返回false
            return Result.fail("手机号格式有误");
        }
        // 2.手机号验证通过：生成验证码；并发送验证码（log日志模拟）；
        //  保存到session--------已用redis替代session共享信息
        // session.setAttribute("code",code);
        String code = RandomUtil.randomNumbers(8);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug(code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
          /*
        注意这里首先也得判断手机号格式是否正确，
         因为保不齐客户在接受短信验证码这段时间修改手机号
         */
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式有误");
        }

        //判断验证码是否正确---从Redis中获取，// String code = loginForm.getCode();

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //String cacheCode = (String) session.getAttribute("code");
        log.debug(cacheCode);

        //如果验证码不匹配
        if (!cacheCode.equals(cacheCode) || cacheCode == null) {
            return Result.fail("您输入的验证码有误");
        }
        //根据手机号查询用户是否存在

        User currentUser = query().eq("phone", loginForm.getPhone().toString()).one();

        //如果不存在，就根据phone创建一个
        if (currentUser == null) {
            currentUser = createUserWithPhone(phone);
        }
        //用户敏感信息处理 ------------------将User类转换为UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(currentUser, UserDTO.class);


        //将用户存储在session中
        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }


    //根据手机号创建用户
    public User createUserWithPhone(String phone) {
        User user = new User();
        String nickName = RandomUtil.randomString(8);
        user.setPhone(phone);
        user.setNickName(nickName);
        save(user);
        return user;

    }


}

