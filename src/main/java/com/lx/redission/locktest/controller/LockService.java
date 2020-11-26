package com.lx.redission.locktest.controller;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 启动看门狗后性能会有所损耗，但是保证了业务超过锁的过期时间，锁提前释放的问题。
 * @author lixiongxiong01
 *
 */
@RestController
@RequestMapping("/lock")
public class LockService {
	@Autowired
	RedissonClient redisson;

	/**
	 * lock()方法默认启动看门狗，并且自动续期
	 * 业务执行完一定要释放，否则会一直续期，产生死锁。（redis宕机或者系统宕机，ttl才会失效）
	 * 
	 * @param taskName 任务名
	 * @param time 模拟业务时间
	 * @return
	 * @throws InterruptedException
	 */
	@RequestMapping("/trylock")
	public boolean lock(String taskName,int time) throws InterruptedException {
		RLock rLock = redisson.getLock("Test-Lock");
		try {
			// 获取锁
			System.out.println(taskName + ":开始执行！");
			
			//1.加锁，必须在finally解锁，否则会一致锁住。除非服务关闭，会取消续期。
			//2.初始过期时间30s,如果当前线程10s内没执行完没释放锁，redisson会默认给ttl续期至30s,直到该线程释放锁。不用担心超过了30s，锁被释放了，导致锁失效
			// 加锁得业务只要运行完成，就不会给当前锁续期，即使不手动解锁，锁默认在30s后自动删除
			rLock.lock();

			System.out.println(taskName+"获取到锁，开始执行任务..");
			// 3.执行业务，模拟执行业务，sleep-10s
			time = 5;
			for (int i = 0; i < time; i++) {
				Thread.sleep(1000 * 1);
				System.out.println(taskName+"执行任务中.."+i+"s");
			}
			
			// 4.返回
			System.out.println(taskName+"执行完成！");
		} catch (Exception e) {
			System.out.println("异常" + e.getMessage());
			return false;
		}finally {
			rLock.unlock();
		}
		return true;
	}

	/**
	 * 设置过期时间，不启动看门狗
	 * 要注意设置的过期时间要比预估的业务执行时间长，不然业务之前完之前被释放，另一个线程进来会同时获取到，即锁失效
	 * 
	 * @param taskName 任务名
	 * @param time  业务执行时间
	 * @return 
	 * @throws InterruptedException
	 */
	@RequestMapping("/lockTime")
	public boolean lockTime(String taskName,int time) {
		RLock rLock = redisson.getLock("Test-Lock-Time");
		try {
			// 获取锁
			System.out.println(taskName + ":开始执行！");
			// 1.设置了过期时间，不会启动看门狗，并且需要手动释放锁。过期时间一定要大于业务预估的时间，否则提前释放了，业务还没执行完，会导致多买多卖
			rLock.lock(30, TimeUnit.SECONDS);// 10秒以后自动解锁，自动解锁时间一定要大于业务时间
			
			for (int i = 0; i < time; i++) {
				// 2.执行业务，模拟执行业务，sleep-10s
				Thread.sleep(1000 * 1);
				System.out.println(taskName+"业务执行中..."+i+"s");
			}
			// 3.返回
			System.out.println(taskName+"业务执行完成");
		} catch (Exception e) {
			System.out.println("异常" + e.getMessage());
			return false;
		} 
		return true;
	}

}
