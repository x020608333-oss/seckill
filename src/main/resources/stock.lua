-- 秒杀库存原子扣减 Lua 脚本
-- 保证"查库存 + 减库存"两个操作原子执行, 彻底杜绝超卖
--
-- KEYS[1]: 库存key, 例如 seckill:stock:1
-- ARGV[1]: 本次扣减数量(秒杀固定为1)
--
-- 返回值:
--   1  -> 扣减成功
--   0  -> 库存不足(或key不存在)
--  -1  -> 库存会被扣成负数(理论上不会发生, 兜底)

local stock = redis.call('GET', KEYS[1])

-- key不存在, 视为无库存(库存未预热或活动不存在)
if (not stock) then
    return 0
end

stock = tonumber(stock)
local num = tonumber(ARGV[1])

if (stock >= num) then
    redis.call('DECRBY', KEYS[1], num)
    return 1
else
    return 0
end
