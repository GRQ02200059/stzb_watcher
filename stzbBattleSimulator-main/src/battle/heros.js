/**
 * 武将配置
 * 
 * Name: 名称
 * Camp: 阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
 * Star: 星级
 * Cost: 统率
 * army: 兵种
 * Limit: 攻击距离
 * Skill: 初始技能
 * Attrs: 初始属性
 * AttrsGrow: 属性成长率
 */

export const __HEROS__ = [
    {   
        id:1001,
        name: "太史慈",
        camp: 3,
        star: 5,
        cost: 25,
        army: 1,
        limit: 5,
        skill: 1004,
        attrs: {
            atk: 98.00,
            def: 89.00,
            int: 67.00,
            spd: 65.00,
            des: 8.00
        },
        attrs_grow: {
            atk: 1.98,
            def: 1.80,
            int: 0.62,
            spd: 1.05,
            des: 0.57
        }
    },
    {
        id:1002,
        name: "马超",
        camp: 5,
        star: 5,
        cost: 30,
        army: 3,
        limit: 3,
        skill: 1003,
        attrs: {
            atk: 97.00,
            def: 91.00,
            int: 45.00,
            spd: 92.00,
            des: 4.00
        },
        attrs_grow: {
            atk: 2.78,
            def: 1.84,
            int: 0.52,
            spd: 1.54,
            des: 0.41
        }
    },
    {
        id:1003,
        name: "刘备",
        camp: 1,
        star: 5,
        cost: 35,
        army: 2,
        limit: 3,
        skill: 1007,
        attrs: {
            atk: 80.00,
            def: 81.00,
            int: 80.00,
            spd: 38.00,
            des: 18.00
        },
        attrs_grow: {
            atk: 1.54,
            def: 1.83,
            int: 1.28,
            spd: 0.92,
            des: 1.73
        }
    },
    {
        id:1004,
        name: "张辽", //名称
        camp: 2, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 35, //统率
        army: 3, //兵种
        limit: 3, //攻击距离
        skill: 1008, //初始技能
        attrs: { //初始属性
            atk: 91.00,
            def: 94.00,
            int: 83.00,
            spd: 105.00,
            des: 3.00
        },
        attrs_grow: { //属性成长率
            atk: 2.21,
            def: 2.27,
            int: 1.05,
            spd: 2.72,
            des: 0.29
        }
    },
    {
        id:1005,
        name: "乐进", //名称
        camp: 2, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 30, //统率
        army: 2, //兵种
        limit: 2, //攻击距离
        skill: 1009, //初始技能
        attrs: { //初始属性
            atk: 96.00,
            def: 87.00,
            int: 67.00,
            spd: 46.00,
            des: 14.00
        },
        attrs_grow: { //属性成长率
            atk: 2.13,
            def: 1.95,
            int: 0.73,
            spd: 0.64,
            des: 1.21
        }
    },
    {
        id:1006,
        name: "魏延", //名称
        camp: 1, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 30, //统率
        army: 2, //兵种
        limit: 2, //攻击距离
        skill: 1010, //初始技能
        attrs: { //初始属性
            atk: 93.00,
            def: 90.00,
            int: 69.00,
            spd: 50.00,
            des: 14.00
        },
        attrs_grow: { //属性成长率
            atk: 1.91,
            def: 1.71,
            int: 1.11,
            spd: 0.80,
            des: 1.10
        }
    },
    {
        id:1007,
        name: "陈到", //名称
        camp: 1, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 30, //统率
        army: 2, //兵种
        limit: 2, //攻击距离
        skill: 1011, //初始技能
        attrs: { //初始属性
            atk: 86.00,
            def: 70.00,
            int: 86.00,
            spd: 41.00,
            des: 13.00
        },
        attrs_grow: { //属性成长率
            atk: 2.36,
            def: 1.75,
            int: 1.20,
            spd: 0.74,
            des: 1.12
        }
    },
    {
        id:1008,
        name: "张机", //名称
        camp: 4, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 30, //统率
        army: 1, //兵种
        limit: 3, //攻击距离
        skill: 1016, //初始技能
        attrs: { //初始属性
            atk: 81.00,
            def: 90.00,
            int: 88.00,
            spd: 55.00,
            des: 12.00
        },
        attrs_grow: { //属性成长率
            atk: 1.17,
            def: 1.79,
            int: 1.63,
            spd: 0.87,
            des: 0.85
        }
    },
    {
        id:1009,
        name: "吕蒙", //名称
        camp: 3, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 35, //统率
        army: 1, //兵种
        limit: 4, //攻击距离
        skill: 1021, //初始技能
        attrs: { //初始属性
            atk: 86.00,
            def: 92.00,
            int: 93.00,
            spd: 54.00,
            des: 9.00
        },
        attrs_grow: { //属性成长率
            atk: 1.66,
            def: 2.23,
            int: 1.64,
            spd: 0.87,
            des: 0.87
        }
    },
    {
        id:1010,
        name: "袁绍", //名称
        camp: 5, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 35, //统率
        army: 1, //兵种
        limit: 3, //攻击距离
        skill: 1022, //初始技能
        attrs: { //初始属性
            atk: 88.00,
            def: 79.00,
            int: 83.00,
            spd: 67.00,
            des: 11.00
        },
        attrs_grow: { //属性成长率
            atk: 1.79,
            def: 1.15,
            int: 1.53,
            spd: 1.23,
            des: 0.97
        }
    },
    {
        id:1011,
        name: "周仓", //名称
        camp: 1, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 30, //统率
        army: 2, //兵种
        limit: 2, //攻击距离
        skill: 1025, //初始技能
        attrs: { //初始属性
            atk: 89.00,
            def: 88.00,
            int: 73.00,
            spd: 48.00,
            des: 17.00
        },
        attrs_grow: { //属性成长率
            atk: 1.81,
            def: 1.80,
            int: 0.89,
            spd: 0.77,
            des: 1.32
        }
    },
    {
        id:1012,
        name: "曹操", //名称
        camp: 2, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 35, //统率
        army: 3, //兵种
        limit: 2, //攻击距离
        skill: 1028, //初始技能
        attrs: { //初始属性
            atk: 80.00,
            def: 107.00,
            int: 90.00,
            spd: 88.00,
            des: 6.00
        },
        attrs_grow: { //属性成长率
            atk: 1.28,
            def: 2.95,
            int: 1.59,
            spd: 1.26,
            des: 0.40
        }
    },
    {
        id:1013,
        name: "陆逊", //名称
        camp: 3, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 30, //统率
        army: 2, //兵种
        limit: 2, //攻击距离
        skill: 1029, //初始技能
        attrs: { //初始属性
            atk: 63.00,
            def: 95.00,
            int: 95.00,
            spd: 40.00,
            des: 17.00
        },
        attrs_grow: { //属性成长率
            atk: 1.30,
            def: 2.09,
            int: 2.09,
            spd: 0.46,
            des: 1.33
        }
    },
    {
        id:1014,
        name: "周瑜", //名称
        camp: 3, //阵营 1=蜀 2=魏 3=吴 4=汉 5=群 6=晋
        star: 5, //星级
        cost: 35, //统率
        army: 2, //兵种
        limit: 2, //攻击距离
        skill: 1030, //初始技能
        attrs: { //初始属性
            atk: 67.00,
            def: 86.00,
            int: 107.00,
            spd: 50.00,
            des: 16.00
        },
        attrs_grow: { //属性成长率
            atk: 0.98,
            def: 2.07,
            int: 2.48,
            spd: 0.44,
            des: 1.70
        }
    }
]