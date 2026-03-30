/**
 * 技能配置
 * 
 * Name: 名称
 * Desc: 描述
 * Level: 战法评级
 * Type: 类型 1=指挥 2=主动 3=追击 4=被动
 * Target: 目标数量 示例值 1=1目标 [2,3]=2-3目标
 * TargetType: 目标类型 self=自身 enemy=敌军 friend=友军(除了自己) 当类型为self时 数量强制为1
 * Limit: 距离 追击战法距离填写0,实际距离按武将攻击距离计算
 * Rate: 发动率 %
 * Study: 是否可学习
 */

import { clacAttackDamage, getRandomBool, calcRecover, getRandomInt, clacSkillAdditionRate, clacInteDamage, clacInte2Damage } from "./battleCalcFunc"
import { keepTwoDecimal, makeSkillTag } from "../uilts";
import { BattleHero } from "./battleHero";


export const __SKILLS__ = [
    {
        id: 1001,
        name: "连战",
        desc: "使自身可以进行两次普通攻击，持续1回合",
        level: "B",
        type: 2,
        target: 1,
        target_type: "self",
        limit: 1,
        rate: 35,
        study: true,
        callskill: function (self) {

        }
    },

    {
        id: 1002,
        name: "温酒斩将",
        desc: "普通攻击后，对攻击目标再次发动猛攻(伤害率200.0%)",
        level: "A",
        type: 3,
        target: 1,
        target_type: "enemy",
        limit: 0,
        rate: 35,
        study: true,
        callskill: function (self, target) {
            self.Manger.Record.pushRecord(self, '的攻击发动【温酒斩将】');
            target.beHurt(self, {
                type: 2,
                rate: 200,
            }, this);
        }
    },

    {
        id: 1003,
        name: "血践黄砂",
        desc: "以无法发动主动战法为代价，使自身进行攻击时的伤害提高120.0%",
        level: "S",
        type: 4,
        target: 1,
        target_type: "self",
        limit: 0,
        rate: "--",
        study: false,
        callskill: function (self) {
            let manger = self.Manger;
            if (manger.Round == "-1") {
                self.Manger.Record.pushRecord(self, '的战法【血践黄砂】生效');
                // 使自身无法发动主动战法 持续到战斗结束
                self.State.activeLimit.rounds = -1
                self.State.activeLimit.from = {
                    hero: self,
                    skille: this
                }
                self.Manger.Record.pushRecord(self, '的无法发动主动战法效果已施加', 1);
                // 增加攻击伤害120%
                self.State.attackDamageAdd.passive = {
                    value: 120,
                    rounds: -1
                }
                self.Manger.Record.pushRecord(self, '造成的攻击伤害提高120%', 1);
            }
        }
    },

    {
        id: 1004,
        name: "方阵突击",
        desc: "普通攻击后，对攻击目标再次发动攻击(伤害率200.0%)，并使其陷入混乱状态，持续1回合",
        level: "A",
        type: 3,
        target: 1,
        target_type: "enemy",
        limit: 0,
        rate: 30,
        study: false,
        callskill: function (self, target) {
            self.Manger.Record.pushRecord(self, '的攻击发动【方阵突击】');
            target.beHurt(self, {
                type: 2,
                rate: 200,
            }, this);

            if (!target.isConfusion()) {
                target.State.confusion = {
                    rounds: 1,
                    from: {
                        hero: self,
                        skill: this
                    }
                }
                self.Manger.Record.pushActionRecord(self, target, '的【方阵突击】使', '陷入混乱1回合', 1);
            } else {
                self.Manger.Record.pushRecord(target, '已存在混乱效果', 1);
            }
        }
    },

    {
        id: 1005,
        name: "先驱突击",
        desc: "战斗开始后前3回合，使自身优先行动，每回合可进行两次普通攻击，并使攻击属性提高30.0",
        level: "A",
        type: 1,
        target: 1,
        target_type: "self",
        limit: 1,
        rate: "--",
        study: true,
        callskill: function (self) {
            let manger = self.Manger;
            if (manger.Round == 0) {
                manger.Record.pushRecord(self, '发动【先驱突击】');

                self.State.firstAction = {
                    rounds: 3, //因为效果的持续回合数在行动前递减+是指挥技能 所以给4回合才能实际生效3次 || ... 突然又正常了 不用给4回合了 ？？？ 有点懵逼 貌似是之前把先手效果改成持续到多少回合 而不是每回合开始减少持续回合了 ... 
                    from: {
                        hero: self,
                        skill: this
                    }
                }
                manger.Record.pushRecord(self, '的先手(预备)效果已施加', 1);

                self.Attrs.atk += 30
                manger.Record.pushRecord(self, `[${self.Name}] 的攻击属性提高了30(${self.Attrs.atk})`, 1);

                self.State.doubleAttack = {
                    rounds: 3,
                    from: {
                        hero: self,
                        skill: this
                    }
                }
                manger.Record.pushRecord(self, '的连击(预备)效果已施加', 1);
            }
        }
    },

    {
        id: 1006,
        name: "钝兵挫锐",
        desc: "普通攻击后，对攻击目标再次发动猛攻(伤害率200.0%)，并使其无法进行普通攻击，持续1回合",
        level: "A",
        type: 3,
        target: 1,
        target_type: "enemy",
        limit: 0,
        rate: 30,
        study: true,
        callskill: function (self, target) {
            self.Manger.Record.pushRecord(self, '的攻击发动【钝兵挫锐】');
            target.beHurt(self, {
                type: 2,
                rate: 200,
            });

            if (!target.isAttackLimit()) {
                target.State.attackLimit = {
                    rounds: 1,
                    from: {
                        hero: self,
                        skill: this
                    }
                }
                self.Manger.Record.pushActionRecord(self, target, '的【钝兵挫锐】使', '陷入怯战1回合', 1);
            } else {
                self.Manger.Record.pushRecord(target, '已存在怯战效果', 1);
            }
        }
    },

    {
        id: 1007,
        name: "皇裔流离",
        desc: "本场战斗中，使我军全体受到伤害时，有50.0%的几率能恢复一定兵力(恢复率68.0%，受谋略属性影响)",
        level: "S",
        type: 1,
        target: 3,
        target_type: "team",
        limit: 0,
        rate: "--",
        study: false,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【皇裔流离】')
            let team;

            if (self.BattleCamp == 'red') {
                team = self.Manger.RedTeam.hero;
            } else {
                team = self.Manger.BlueTeam.hero;
            }

            team.forEach(e => {
                let subskill = () => {
                    if (getRandomBool(self.getRealSkillRate(50))) {
                        let revocer = calcRecover(self, 68, 0.6)
                        e.revocer(revocer, self, '皇裔流离');
                    }
                }
                // e.ON_HURT.push(subskill);
                e.addHook("受伤时", "急救", subskill, this, self)
                self.Manger.Record.pushRecord(e, '的急救效果已施加', 1)
            });
        },
    },

    {
        id: 1008,
        name: "其疾如风",
        desc: "战斗开始后前3回合，使我军全体速度属性提高41(受谋略属性影响)，并使其每回合有70%的几率可以进行两次普通攻击",
        level: "S",
        type: 1,
        target: 1,
        target_type: "team",
        limit: 0,
        rate: "--",
        study: false,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【其疾如风】')
            let team;

            if (self.BattleCamp == 'red') {
                team = self.Manger.RedTeam.hero;
            } else {
                team = self.Manger.BlueTeam.hero;
            }

            let spdadd = 41 + (Math.floor((self.Attrs.spd - 80) * 0.075));

            team.forEach(e => {
                e.Attrs.spd = keepTwoDecimal(spdadd + e.Attrs.spd);
                self.Manger.Record.pushRecord(e, `的速度属性提高了${spdadd}(${e.Attrs.spd})`, 1);

                /*  e.BEFORE_BASEATK.push(() => {
                     if (self.Manger.Round >= 1 && self.Manger.Round <= 3) {
                         if (getRandomBool(70)) {
                             if (e.State.doubleAttack.rounds <= 0) {
                                 e.State.doubleAttack = {
                                     rounds: 1,
                                     from: {
                                         hero: self,
                                         skill: 1008
                                     }
                                 }
                             }
                         } else {
                             self.Manger.Record.pushRecord(e, '的其疾如风未生效', 0)
                         }
                     }
                 }); */

                e.addHook("普攻前", "连击效果", () => {
                    if (self.Manger.Round >= 1 && self.Manger.Round <= 3) {
                        if (getRandomBool(self.getRealSkillRate(70))) {
                            if (e.State.doubleAttack.rounds <= 0) {
                                e.State.doubleAttack = {
                                    rounds: 1,
                                    from: {
                                        hero: self,
                                        skill: this
                                    }
                                }
                            }
                        } else {
                            self.Manger.Record.pushRecord(e, '的其疾如风未生效', 0)
                        }
                    }
                }, this, self);

                self.Manger.Record.pushRecord(e, '的连击(预备)效果已施加', 1)
            });
        },
    },

    {
        id: 1009,
        name: "奋疾先登",
        desc: "每回合行动时使自身攻击伤害提升8.0%，自身速度每高于一名武将，则攻击伤害额外提升8.0%。该效果提升至40%时，将对距离3以内的敌军群体发动一次攻击（伤害率190.0%），发动后攻击伤害提升效果消失，同时使目标速度属性降低20.0，该效果可叠加并持续直到战斗结束",
        level: "S",
        type: 1,
        target: 1,
        target_type: "self",
        limit: 0,
        rate: "--",
        study: false,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【奋疾先登】')
            let damageRate = 190;
            let maxAddDamageRate = 40;
            let addDamageRate = 8;
            let stateName = self.getStateName("attackDamageAdd");

            let subskill = () => {
                let attack = () => {
                    //TODO 考虑暴走
                    let targets = self.getTarget(3, 2);
                    targets.forEach(e => {
                        self.Manger.Record.pushActionRecord(self, self, '执行来自', '的【奋疾先登】效果');
                        e.beHurt(self, {
                            type: 1,
                            rate: damageRate
                        }, this);
                        e.Attrs.spd = keepTwoDecimal(e.Attrs.spd - 20);
                        if (e.Attrs.spd < 0) e.Attrs.spd = 0;
                        self.Manger.Record.pushActionRecord(self, e, '【奋疾先登】的效果使', `的速度属性降低了20(${e.Attrs.spd})`, 1);
                    });
                    self.delState("attackDamageAdd", this);
                    // self.Manger.Record.pushActionRecord(self, self, `来自`, `【${this.name}】的${stateName}效果消失了`, 1);
                }

                self.addState("attackDamageAdd", addDamageRate, -1, this, self, true)
                // self.Manger.Record.pushActionRecord(self, self, `【${this.name}】使`, `造成的${stateName}${self.getState("attackDamageAdd", this.type)}%`);
                if (self.State.attackDamageAdd.command.value >= maxAddDamageRate) attack()

                self.Manger.SortSpdHeros.forEach((e, i) => {
                    if (e != self && e.Arms > 0) {
                        if (self.Attrs.spd > e.Attrs.spd) {
                            self.Manger.Record.pushActionRecord(e, self, '执行来自', '的【奋疾先登】效果');
                            self.addState("attackDamageAdd", addDamageRate, -1, this, self, true);
                            // self.Manger.Record.pushActionRecord(self, self, `【${this.name}】使`, `造成的${stateName}${self.getState("attackDamageAdd", this.type)}%`);
                            if (self.getState("attackDamageAdd", this.type) >= maxAddDamageRate) attack()
                        }
                    }
                })
            }

            // self.ON_ACTION.push(subskill)
            self.addHook("行动时", "增伤效果", subskill, this, self)
        },
    },

    {
        id: 1010,
        name: "奇兵拒北",
        desc: "每回合行动时有30.0%几率对敌军大营和中军分别发动一次攻击（伤害率180.0%），同时使速度最高的友军单体对敌军大营及中军分别发动一次攻击（伤害率120.0%-180.0%）。每回合奇兵拒北未生效时可提升其5%的发动率，可叠加，生效后发动率提升效果消失",
        level: "S",
        type: 1,
        target: 1,
        target_type: "self",
        limit: 0,
        rate: "--",
        study: false,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【奇兵拒北】')
            let rate = 30;
            let addRate = 5;
            let damageRate = 180;
            let teamMinDamageRate = 120;
            let teamMaxDamageRate = 180;

            let subskill = () => {
                let currentRate = self.getRealSkillRate(rate) + (self.RATE_VAL[1010] ? self.RATE_VAL[1010].value : 0);
                self.Manger.Record.pushRecord(self, '的【奇兵拒北】当前生效几率为(' + currentRate + '%)')
                if (getRandomBool(currentRate)) {
                    self.Manger.Record.pushActionRecord(self, self, '执行来自', '的【奇兵拒北】效果');
                    let targets;
                    let team;
                    if (self.BattleCamp == 'red') {
                        targets = self.Manger.BlueTeam.hero;
                        team = self.Manger.RedTeam.hero;
                    } else {
                        targets = self.Manger.RedTeam.hero;
                        team = self.Manger.BlueTeam.hero;
                    }
                    // console.log("【奇兵拒北】效果","debug",targets);
                    // 自身执行效果
                    targets.forEach(e => {
                        if ((e.Posname == '大营' || e.Posname == '中军') && e.Arms > 0) {
                            // console.log("【奇兵拒北】效果","debug",e);
                            e.beHurt(self, {
                                type: 1,
                                rate: damageRate
                            }, this);
                        }
                    })
                    let spdhero = null;
                    team.forEach(e => {
                        if (e != self && e.Arms > 0) {
                            if (spdhero == null) {
                                spdhero = e;
                            } else if (e.Attrs.spd > spdhero.Attrs.spd) {
                                spdhero = e;
                            }
                        }
                    });
                    // 速度最快友军执行效果 暂时没考虑暴走
                    self.Manger.Record.pushActionRecord(spdhero, self, '执行来自', '的【奇兵拒北】效果');
                    targets.forEach(e => {
                        if ((e.Posname == '大营' || e.Posname == '中军') && e.Arms > 0) {
                            e.beHurt(self, {
                                type: 1,
                                rate: getRandomInt(teamMinDamageRate, teamMaxDamageRate)
                            }, this);
                        }
                    })

                    if (self.RATE_VAL[1010]) {
                        delete self.RATE_VAL[1010]
                    }
                } else {
                    self.Manger.Record.pushRecord(self, '的【奇兵拒北】没有生效')
                    if (self.RATE_VAL[1010]) {
                        self.RATE_VAL[1010].value += addRate;
                    } else {
                        self.RATE_VAL[1010] = {
                            value: addRate,
                            rounds: -1
                        }
                    }
                }
            }

            // self.ON_ACTION.push(subskill)
            self.addHook("行动时", "攻击效果", subskill, this, self)
        },
    },

    {
        id: 1011,
        name: "忠克猛烈",
        desc: "本战法造成的伤害无视兵种相克及目标的防御属性；对敌军单体发动1次攻击(伤害率280.0%)，并使其陷入犹豫状态，持续1回合；直到陈到下回合行动前，目标每受到1次攻击伤害，陈到对其发动1次攻击(伤害率80.0%)，期间最多可触发3次",
        level: "S",
        type: 2,
        target: 1,
        target_type: "enemy",
        limit: 4,
        rate: 50,
        study: false,
        callskill: function (self) {
            if (getRandomBool(this.rate)) {
                self.Manger.Record.pushRecord(self, '发动【忠克猛烈】')
                // 战法攻击效果
                let target = self.getTarget(4, 1);
                target[0].beHurt(self, {
                    type: 1,
                    rate: 280,
                }, this)
                // 使其陷入犹豫1回合
                if (!target[0].isActiveLimit()) {
                    target[0].State.activeLimit = {
                        rounds: 1,
                        from: {
                            hero: self,
                            skill: this
                        }
                    }
                    self.Manger.Record.pushActionRecord(self, target[0], '的【忠克猛烈】使', '陷入犹豫1回合');
                } else {
                    self.Manger.Record.pushRecord(target[0], '已存在犹豫效果', 1);
                }

                // 施加受到伤害时陈到对其发动攻击
                let subskill = (attacker, damageInfo, skill) => {
                    // 需判处此伤害不是自己的【忠克猛烈】触发的,避免死循环
                    if (skill == this && attacker == self) return;
                    // 期间最多触发3次
                    if (self.countGet(makeSkillTag(self, this, "受伤效果计次")) >= 3) return;
                    self.countAdd(makeSkillTag(self, this, "受伤效果计次"));
                    self.Manger.Record.pushActionRecord(self, self, '执行来自', '的【忠克猛烈】效果');
                    target[0].beHurt(self, {
                        type: 1,
                        rate: 80,
                    }, this);

                }

                target[0].addHook("受伤时", "目标受伤时效果", subskill, this, self, 'debuff', false)
                // 陈到行动前 清除所有人的 【忠克猛烈】施加的 "受到伤害时陈到对其发动攻击" 的效果
                let clear = () => {
                    self.Manger.SortSpdHeros.forEach(e => {
                        e.clearHook("受伤时", makeSkillTag(self, this, "目标受伤时效果"));
                    });
                    self.countRest(makeSkillTag(self, this, "受伤效果计次"));
                }
                self.addHook("行动前", makeSkillTag(self, this, "清除"), clear, this, self, 'other');
                return true;
            }
        },
    },

    {
        id: 1012,
        name: "愈战愈勇",
        desc: "使自身进行攻击的伤害提高10.0%，此效果每回合开始时额外叠加一次，持续直到战斗结束",
        level: "A",
        type: 4,
        target: 1,
        target_type: "self",
        limit: 0,
        rate: "--",
        study: true,
        callskill: function (self) {
            let manger = self.Manger;
            if (manger.Round == "-1") {
                self.Manger.Record.pushRecord(self, '的战法【愈战愈勇】生效');
                // 增加攻击伤害10%
                self.State.attackDamageAdd.passive = {
                    value: 10,
                    rounds: -1
                }
                self.Manger.Record.pushRecord(self, '造成的攻击伤害提高10%', 1);
                let subskill = () => {
                    self.State.attackDamageAdd.passive.value += 10;
                    self.Manger.Record.pushActionRecord(self, self, '的【愈战愈勇】使', '造成的攻击伤害提高' + self.State.attackDamageAdd.passive.value + '%');
                }

                self.addHook("回合开始时", "回合开始时添加攻击伤害提高", subskill, this, self);
            }
        }
    },

    {
        id: 1013,
        name: "始计",
        desc: "战斗前4回合，在自身行动前，使我军大营下一次攻击或策略攻击时的伤害提高20.0%（受谋略属性影响），敌方兵力最多单体下一次攻击或策略攻击时的伤害降低30.0%（受谋略属性影响），并在自身受到攻击或策略攻击伤害后，于本回合内进入洞察状态，免疫混乱、犹豫、怯战、暴走和挑衅效果",
        level: "S",
        type: 1,
        target: 1,
        target_type: "self",
        limit: 0,
        rate: "--",
        study: true,
        callskill: function (self) {
            let manger = self.Manger;
            if (manger.Round == 0) {
                self.Manger.Record.pushRecord(self, '发动【始计】');
                let addRate = clacSkillAdditionRate(20, 0.15, self.Attrs.int);
                let subRate = clacSkillAdditionRate(30, 0.25, self.Attrs.int);

                let stateName = self.getStateName("attackDamageAdd");
                let stateName2 = self.getStateName("inteDamageAdd");

                let delAddRate = (attacker) => {
                    let obj = attacker.getState("attackDamageAdd", this.type, true);
                    if (obj.from == this && obj.hero == self) {
                        attacker.delState("attackDamageAdd", this, true);
                        attacker.delState("inteDamageAdd", this, true);
                        // self.Manger.Record.pushActionRecord(attacker, self, `来自`, `【${this.name}】的${stateName}效果消失了`, 1);
                        // self.Manger.Record.pushActionRecord(attacker, self, `来自`, `【${this.name}】的${stateName2}效果消失了`, 1);
                    }
                    attacker.clearHook("攻击后", makeSkillTag(self, this, "攻击后移除增伤"));
                }

                let subskill = () => {
                    if (manger.Round > 4) return;
                    self.Manger.SortSpdHeros.forEach(e => {
                        //TODO 考虑暴走
                        if (e.BattleCamp == self.BattleCamp && e.Posname == '大营') {
                            let ret = e.addState("attackDamageAdd", addRate, 1, this, self, false, 2);
                            let ret2 = e.addState("inteDamageAdd", addRate, 1, this, self, false, 2);
                            // if (ret) self.Manger.Record.pushActionRecord(self, e, `【${this.name}】使`, `造成的${stateName}${ret.value}%`);
                            // if (ret2) self.Manger.Record.pushActionRecord(self, e, `【${this.name}】使`, `造成的${stateName2}${ret2.value}%`);
                            e.addHook("攻击后", "攻击后移除增伤", delAddRate, this, self, "other");
                        }
                    })
                }
                self.addHook("行动前", "行动前添加攻击伤害增加", subskill, this, self);
                //TODO 敌方最高兵力施加造成伤害降低 & 自身受到攻击后洞察
            }
        }
    },

    {
        id: 1014,
        name: "浑水摸鱼",
        desc: "1回合准备，使敌军群体陷入混乱状态，持续2回合",
        level: "S",
        type: 2,
        target: 2,
        target_type: "enemy",
        limit: 4,
        rate: 35,
        study: true,
        callskill: function (self) {
            // 准备型战法先创建子技能方法 然后提交在准备战法效果执行堆里
            if (getRandomBool(this.rate)) {
                let subskill = () => {
                    // 先获取目标
                    let targets = self.getTarget(this.limit, this.target);

                    targets.forEach(target => {
                        if (!target.isConfusion()) {
                            target.State.confusion = {
                                rounds: 2,
                                from: this
                            }
                            self.Manger.Record.pushRecord(target, `陷入混乱2回合`, 1);
                        } else {
                            self.Manger.Record.pushRecord(target, '已存在混乱效果', 1);
                        }
                    })
                }

                self.addReadySkill(this, 1, subskill);
                return true;
            }
        }
    },

    {
        id: 1015,
        name: "垒实迎击",
        desc: "受到普通攻击伤害时，有50.0%几率使自身恢复一定兵力（恢复率200.0%）；有50.0%几率移除自身由主动及追击战法带来的负面效果；使自身进入规避状态，有50.0%几率免疫下1次受到的伤害。同时当自身位于中军及前锋时，每回合开始时有50.0%几率援护友军全体，持续1回合",
        level: "S",
        type: 4,
        target: 1,
        target_type: "self",
        limit: 1,
        rate: "--",
        study: true,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, `发动【${this.name}】`);
            let subskill = (attacker, damageInfo, skill) => {
                if (damageInfo.type == 1 && skill == null) {
                    // 恢复一定兵力
                    if (getRandomBool(self.getRealSkillRate(50))) {
                        self.Manger.Record.pushRecord(self, `恢复一定兵力`);
                        self.revocer(calcRecover(self, 200, 0, 2), self, this.name);
                    }

                    // 移除负面效果
                    if (getRandomBool(self.getRealSkillRate(50))) {
                        self.Manger.Record.pushRecord(self, `移除负面效果`);
                        self.clearDebuff(this, self);
                    }

                    // 添加一个在受伤前触发的 50%规避效果的子技能 触发后移除该子技能

                }
            }

            let subskill2 = () => {
                // 援护友军
                if (getRandomBool(self.getRealSkillRate(50))) {

                }
            }

            self.addHook("受伤时", "受伤时效果", subskill, this, self);
            self.addHook("回合开始时", "回合开始时效果", subskill2, this, self);
        }
    },

    {
        id: 1016,
        name: "金匮要略",
        desc: "战斗开始后前3回合，使我军全体受到的所有伤害降低20.4%（受谋略属性影响），同时使我军全体受到伤害时，有50.0%的几率能恢复一定兵力（恢复率80.0%，受谋略属性影响）",
        level: "S",
        type: 1,
        target: 3,
        target_type: "team",
        limit: 3,
        rate: "--",
        study: false,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【金匮要略】')
            let team;
            if (self.BattleCamp == 'red') {
                team = self.Manger.RedTeam.hero;
            } else {
                team = self.Manger.BlueTeam.hero;
            }
            let revocerRate = 80;
            let revocerRateAdd = 0.75;
            let damageSubRate = 20.4;
            let damageSubRateAdd = 0.13;


            team.forEach(e => {
                let subskill = () => {
                    if (getRandomBool(self.getRealSkillRate(50))) {
                        let revocer = calcRecover(self, revocerRate, revocerRateAdd)
                        e.revocer(revocer, self, '金匮要略');
                    }
                }
                e.addHook("受伤时", "急救", subskill, this, self);
                e.addHook("行动前", "移除急救", () => {
                    if (e.Manger.Round >= 4) {
                        e.clearHook("受伤时", makeSkillTag(self, this, "急救"))
                    }
                }, this, self);
                let value = clacSkillAdditionRate(damageSubRate, damageSubRateAdd, self.Attrs.int);
                console.log("debug", value);
                e.addState("beAttackDamageSub", value, 3, this, self)
                e.addState("beInteDamageSub", value, 3, this, self)
                self.Manger.Record.pushRecord(e, '的急救效果已施加', 1);
            });
        },
    },

    {
        id: 1017,
        name: "神兵天降",
        desc: "战斗开始后前3回合，使敌军群体受到攻击和策略攻击时的伤害提高30.0%（受谋略属性影响）",
        level: "S",
        type: 1,
        target: 2,
        target_type: "team",
        limit: 4,
        rate: "--",
        study: true,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【神兵天降】')
            let damageAddRate = 30;
            let damageAddRateAdd = 0.15;

            let enemy = self.getTarget(4, 2);

            enemy.forEach(e => {
                let value = clacSkillAdditionRate(damageAddRate, damageAddRateAdd, self.Attrs.int);
                e.addState("beAttackDamageAdd", value, 3, this, self)
                e.addState("beInteDamageAdd", value, 3, this, self)
            });
        },
    },

    {
        id: 1018,
        name: "大赏三军",
        desc: "战斗开始后前3回合，使我军群体进行攻击和策略攻击时的伤害提高30.0%（受谋略属性影响）",
        level: "S",
        type: 1,
        target: 2,
        target_type: "team",
        limit: 3,
        rate: "--",
        study: true,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【大赏三军】')
            let damageAddRate = 30;
            let damageAddRateAdd = 0.15;

            let team = self.getTarget(3, 2, 2);

            team.forEach(e => {
                let value = clacSkillAdditionRate(damageAddRate, damageAddRateAdd, self.Attrs.int);
                e.addState("attackDamageAdd", value, 3, this, self)
                e.addState("inteDamageAdd", value, 3, this, self)
            });
        },
    },
    {
        id: 1019,
        name: "无心恋战",
        desc: "战斗开始后前3回合，使敌军群体进行攻击和策略攻击时的伤害降低30.0%（受谋略属性影响）",
        level: "S",
        type: 1,
        target: 2,
        target_type: "team",
        limit: 4,
        rate: "--",
        study: true,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【无心恋战】')
            let damageSubRate = 30;
            let damageSubRateAdd = 0.15;

            let enemy = self.getTarget(4, 2);

            enemy.forEach(e => {
                let value = clacSkillAdditionRate(damageSubRate, damageSubRateAdd, self.Attrs.int);
                e.addState("attackDamageSub", value, 3, this, self)
                e.addState("inteDamageSub", value, 3, this, self)
            });
        },
    },

    {
        id: 1020,
        name: "避其锋芒",
        desc: "战斗开始后前3回合，使我军群体受到攻击和策略攻击时的伤害降低30.0%（受谋略属性影响）",
        level: "S",
        type: 1,
        target: 2,
        target_type: "team",
        limit: 3,
        rate: "--",
        study: true,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【避其锋芒】')
            let damageSubRate = 30;
            let damageSubRateAdd = 0.15;

            let team = self.getTarget(3, 2, 2);

            team.forEach(e => {
                let value = clacSkillAdditionRate(damageSubRate, damageSubRateAdd, self.Attrs.int);
                e.addState("beAttackDamageSub", value, 3, this, self)
                e.addState("beInteDamageSub", value, 3, this, self)
            });
        },
    },

    {
        id: 1021,
        name: "白衣渡江",
        desc: "战斗开始后前2回合，使敌军群体无法进行普通攻击，在此效果结束后，将对敌军全体发动一次强力策略攻击（伤害率215.0%，受谋略属性影响），造成的伤害无视规避",
        level: "S",
        type: 1,
        target: 2,
        target_type: "enemy",
        limit: 5,
        rate: "--",
        study: false,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【白衣渡江】')

            let enemy = self.getTarget(5, 2);

            enemy.forEach(e => {
                let value = clacSkillAdditionRate(215, 2.25, self.Attrs.int);
                if (!e.isAttackLimit()) {
                    e.State.attackLimit = {
                        rounds: 2,
                        from: {
                            hero: self,
                            skill: this
                        }
                    }
                    self.Manger.Record.pushActionRecord(self, e, `的【${this.name}】使`, '陷入怯战2回合', 1);
                } else {
                    self.Manger.Record.pushRecord(e, '已存在怯战效果', 1);
                }

                let damageInfo = {
                    type: 2,
                    rate: value
                };
                let damage = clacInteDamage(self, e, damageInfo, this);
                e.addHook("行动时", "策略攻击伤害", () => {
                    if (e.Manger.Round == 3) {
                        e.beHurtByNum(self, damageInfo, this, damage, () => {
                            self.Manger.Record.pushActionRecord(self, e, `由于`,`【${this.name}】施加的效果损失了${damage}兵力(${e.Arms})`);
                        });
                        
                    }
                }, this, self, "debuff");
                self.Manger.Record.pushRecord(e, "的受到策略攻击伤害效果已施加", 1);
            });
        },
    },

    {
        id: 1022,
        name: "威震河朔",
        desc: "对敌军群体发动一次攻击（伤害率200.0%），使自身与友军单体主动战法伤害提升20.0%（受攻击属性影响），持续2回合。此战法每发动一次，其发动率降低10.0%",
        level: "A",
        type: 2,
        target: 2,
        target_type: "enemy",
        limit: 5,
        rate: 70,
        study: false,
        callskill: function (self) {
            let tag = makeSkillTag(self, this, "发动次数");
            let currentRate = this.rate + (self.RATE_VAL[this.id] ? self.RATE_VAL[this.id].value : 0);
            if (getRandomBool(currentRate)) {
                self.Manger.Record.pushRecord(self, '发动【威震河朔】')
                self.countAdd(tag, 1);
                self.RATE_VAL[this.id] = {
                    value: -10 * self.countGet(tag),
                    rounds: -1
                }
                let enemy = self.getTarget(5, 2);
                enemy.forEach(e => {
                    e.beHurt(self, {
                        type: 1,
                        rate: 200
                    }, this);
                });
                //QUESTION 该技能在攻略里没找到成长值 成长值大概为这个 可能有些许误差
                let value = clacSkillAdditionRate(20, 0.125, self.Attrs.atk);
                self.addState("activeDamageAdd", value, 2, this, self);
                let targets = self.getTarget(5, 1, 3);
                if (targets.length > 0) {
                    targets[0].addState("activeDamageAdd", value, 2, this, self);
                }
                return true;
            }
        },
    },

    {
        id: 1023,
        name: "反计之策",
        desc: "战斗开始后前3回合，使敌军群体发动主动战法时造成的伤害大幅下降，并在首回合有100.0%的几率使其陷入犹豫状态，无法发动主动战法",
        level: "S",
        type: 1,
        target: 2,
        target_type: "enemy",
        limit: 4,
        rate: "--",
        study: true,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, '发动【反计之策】')
            let enemy = self.getTarget(4, 2);

            enemy.forEach(e => {
                e.addState("activeDamageSub", 9999, 3, this, self)
                if (!e.isActiveLimit()) {
                    e.State.activeLimit = {
                        rounds: 1,
                        from: {
                            hero: self,
                            skill: this
                        }
                    }
                    self.Manger.Record.pushActionRecord(self, e, `的【${this.name}】使`, '陷入犹豫1回合', 1);
                } else {
                    self.Manger.Record.pushRecord(e, '已存在犹豫效果', 1);
                }
            });
        },
    },

    {
        id: 1024,
        name: "百战精兵",
        desc: "使自身攻击属性、防御属性、谋略属性、速度属性全部提高32.0",
        level: "B",
        type: 1,
        target: 1,
        target_type: "self",
        limit: 1,
        rate: "--",
        study: true,
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, `发动【${this.name}】`);
            self.Attrs.atk += 32;
            self.Attrs.int += 32;
            self.Attrs.def += 32;
            self.Attrs.spd += 32;
            self.Manger.Record.pushActionRecord(self, self, `【${this.name}】的效果使`, `的攻击属性提高了32(${self.Attrs.atk})`, 1);
            self.Manger.Record.pushActionRecord(self, self, `【${this.name}】的效果使`, `的防御属性提高了32(${self.Attrs.def})`, 1);
            self.Manger.Record.pushActionRecord(self, self, `【${this.name}】的效果使`, `的谋略属性提高了32(${self.Attrs.int})`, 1);
            self.Manger.Record.pushActionRecord(self, self, `【${this.name}】的效果使`, `的速度属性提高了32(${self.Attrs.spd})`, 1);
        },
    },

    {
        id: 1025, //ID
        name: "持刀从武", //名称
        desc: "自身每回合行动时，每次有50.0%概率对友军大营上次行动阶段造成伤害的目标发动一次攻击（伤害率120.0%），重复三次，每次目标独立判定。当该攻击目标未处于控制状态时，对同一目标的伤害率每次递减20.0%", //描述
        level: "A", //战法级别
        type: 4, //战法类型 1=指挥 2=主动 3=追击 4=被动
        target: 1, //目标数量
        target_type: "enemy", //作用目标
        limit: 5, //战法距离
        rate: "--", //战法发动率
        study: false, //战法是否可学习
        callskill: function (self) {
            //给友军大营添加造成伤害时记录目标
            let team = self.BattleCamp == "blue" ? self.Manger.BlueTeam.hero : self.Manger.RedTeam.hero;
            team[0].Storage[this.id] = {
                hero: [],
                clear: false
            };

            team[0].addHook("造成伤害后", "造成伤害后记录目标", (attacker, target) => {
                if (team[0].Storage[this.id].clear == true) {
                    team[0].Storage[this.id].clear = false;
                    team[0].Storage[this.id].hero = [];
                }
                let add = true;
                team[0].Storage[this.id].hero.forEach(e => {
                    if (e == target) {
                        console.log('debug', "已有目标", target, `第${self.Manger.Round}回合`);
                        add = false;
                        return
                    }
                });
                if (add) {
                    console.log('debug', "添加目标", target, `第${self.Manger.Round}回合`);
                    team[0].Storage[this.id].hero.push(target)
                }
            }, this, self);

            team[0].addHook("回合开始时", "设置清除标记", () => {
                team[0].Storage[this.id].clear = true;
            }, this, self);

            //周仓造成伤害
            let subskill = () => {
                let damageRate = 120;
                for (let index = 0; index < 3; index++) {
                    if (getRandomBool(self.getRealSkillRate(50))) {
                        if (team[0].Storage[this.id].hero.length > 0) {
                            let atkTarget = team[0].Storage[this.id].hero[Math.floor(Math.random() * team[0].Storage[this.id].hero.length)];
                            console.log('debug', team[0].Storage[this.id].hero, `第${self.Manger.Round}回合`);
                            self.Manger.Record.pushActionRecord(self, self, '执行来自', `的【${this.name}】效果`);
                            self.Manger.Record.pushRecord(self, `【${this.name}】当前伤害率${damageRate}%`)
                            atkTarget.beHurt(self, {
                                type: 1,
                                rate: damageRate
                            }, this);
                            //TODO 差一个暴走的控制判断
                            //如果目标不处于控制状态 伤害率降低20%
                            if (!atkTarget.isActiveLimit() && !atkTarget.isActiveLimit() && !atkTarget.isConfusion()) {
                                damageRate -= 20;
                            }
                        }
                    } else {
                        self.Manger.Record.pushRecord(self, `【${this.name}】的效果未生效`)
                    }
                }
            }

            self.addHook("行动时", "行动时概率造成伤害", subskill, this, self)
        },
    },

    {
        id: 1026, //ID
        name: "一骑当千", //名称
        desc: "1回合准备，使敌军群体陷入混乱状态，持续2回合", //描述
        level: "S", //战法级别
        type: 2, //战法类型 1=指挥 2=主动 3=追击 4=被动
        target: 3, //目标数量
        target_type: "enemy", //作用目标
        limit: 5, //战法距离
        rate: 30, //战法发动率
        study: true, //战法是否可学习
        callskill: function (self) {
            // 准备型战法先创建子技能方法 然后添加在准备战法效果执行堆里
            if (getRandomBool(this.rate)) {
                let subskill = () => {
                    // 先获取目标
                    let targets = self.getTarget(this.limit, this.target);

                    targets.forEach(target => {
                        target.beHurt(self, {
                            type: 1,
                            rate: 280
                        }, this);
                    })
                }

                self.addReadySkill(this, 1, subskill);
                return true;
            }
        }
    },

    {
        id: 1027, //ID
        name: "三军之众", //名称
        desc: "1回合准备，使我军单体恢复4次兵力（恢复率151.0%，受谋略属性影响），每次目标独立判定", //描述
        level: "S", //战法级别
        type: 2, //战法类型 1=指挥 2=主动 3=追击 4=被动
        target: 1, //目标数量
        target_type: "team", //作用目标
        limit: 3, //战法距离
        rate: 45, //战法发动率
        study: true, //战法是否可学习
        callskill: function (self) {
            let revocerRate = 151;
            let revocerRateAdd = 1.575;

            if (getRandomBool(this.rate)) {
                let subskill = () => {
                    for (let index = 0; index < 4; index++) {
                        let targets = self.getTarget(this.limit, this.target, 2);

                        targets.forEach(target => {
                            let revocer = calcRecover(self, revocerRate, revocerRateAdd, 2)
                            target.revocer(revocer, self, '三军之众');
                        });
                    }
                }

                self.addReadySkill(this, 1, subskill);
                return true;
            }
        }
    },

    {
        id: 1028, //ID
        name: "魏武之世", //名称
        desc: "在本场战斗中，使敌军全体攻击属性、防御属性、谋略属性、速度属性下降15.0%（受谋略属性影响），并使我军全体攻击距离+1", //描述
        level: "S", //战法级别
        type: 1, //战法类型 1=指挥 2=主动 3=追击 4=被动
        target: 3, //目标数量
        target_type: "enemy", //作用目标
        limit: 5, //战法距离
        rate: "--", //战法发动率
        study: false, //战法是否可学习
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, `发动【${this.name}】`);
            let enemy = self.BattleCamp == "blue" ? self.Manger.RedTeam.hero : self.Manger.BlueTeam.hero;
            let subrate = clacSkillAdditionRate(15, 0.045, self.Attrs.int);

            enemy.forEach(e => {
                for (const key in e.Attrs) {
                    let oattr = e.Attrs[key];
                    // e.Attrs[key] *= (100 - subrate) / 100;
                    let subattr = e.Attrs[key] * subrate / 100
                    e.Attrs[key] -= subattr;
                    self.Manger.Record.pushRecord(e, `的${key}属性降低了${subrate}%(${subattr})(${e.Attrs[key]})`, 1);
                }
            });
        }
    },

    {
        id: 1029, //ID
        name: "火势风威", //名称
        desc: "1回合准备，对敌军全体发动策略攻击（伤害率111.0%，受谋略属性影响），并使其在受到下一次伤害时，会额外引发一次燃烧（伤害率221.0%，受谋略属性影响）", //描述
        level: "S", //战法级别
        type: 2, //战法类型 1=指挥 2=主动 3=追击 4=被动
        target: 3, //目标数量
        target_type: "enemy", //作用目标
        limit: 5, //战法距离
        rate: 40, //战法发动率
        study: false, //战法是否可学习
        callskill: function (self) {
            // 准备型战法先创建子技能方法 然后添加在准备战法效果执行堆里
            let mainDamage = 111;
            let mainDamageAdd = 0.95;
            let fireDamage = 221;
            let fireDamageAdd = 2.45;

            if (getRandomBool(this.rate)) {
                let subskill = () => {
                    // 先获取目标
                    let targets = self.getTarget(this.limit, this.target);
                    let value = clacSkillAdditionRate(mainDamage, mainDamageAdd, self.Attrs.int);
                    let value2 = clacSkillAdditionRate(fireDamage, fireDamageAdd, self.Attrs.int);

                    targets.forEach(target => {
                        target.beHurt(self, {
                            type: 2,
                            rate: value
                        }, this);
                    });
                    
                    targets.forEach(target => {
                        let damageInfo = {
                            type: 4,
                            rate: value2
                        };
                        let damage = clacInte2Damage(self, target, damageInfo, this);
                        let tag = target.addHook("受伤时","受伤时燃烧",() => {
                            //需先清除执行堆或者判断该伤害不是自己触发的否则会死循环
                            target.clearHook("受伤时",tag);
                            target.beHurtByNum(self,damageInfo,this,damage,() => {
                                self.Manger.Record.pushActionRecord(target,self,`由于`,`【${this.name}】施加的燃烧效果损失了${damage}兵力(${target.Arms})`,1);
                            });
                        },this, self, "debuff");
                        self.Manger.Record.pushRecord(target,`的燃烧效果已施加`,1);
                        target.callHook('被施加持续伤害时',this,self,damage,damageInfo,"燃烧");
                    });
                }

                self.addReadySkill(this, 1, subskill);
                return true;
            }
        }
    },

    {
        id: 1030, //ID
        name: "衔命建功", //名称
        desc: "敌军全体每回合首次受到持续性伤害时，周瑜有50.0%几率对其发动一次策略攻击（伤害率140.0%，受谋略属性影响）；第3回合起，敌军武将陷入持续性伤害时，立即额外引发一次该持续性伤害", //描述
        level: "S", //战法级别
        type: 1, //战法类型 1=指挥 2=主动 3=追击 4=被动
        target: 3, //目标数量
        target_type: "enemy", //作用目标
        limit: 5, //战法距离
        rate: "--", //战法发动率
        study: false, //战法是否可学习
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, `发动【${this.name}】`);
            let enemy = self.BattleCamp == "blue" ? self.Manger.RedTeam.hero : self.Manger.BlueTeam.hero;
            let value = clacSkillAdditionRate(140, 0.8, self.Attrs.int);
            self.Storage[this.id] = {
                hero: {}
            };
            
            enemy.forEach(e => {
                //敌军武将陷入持续性伤害时，立即额外引发一次该持续性伤害
                e.addHook('被施加持续伤害时','立即额外引发一次该持续性伤害',(skill, hero, damage, damageInfo, damageName) => {
                    if(self.Manger.Round >= 3){
                        self.Manger.Record.pushRecord(e,`的${damageName}效果已被触发`,1);
                        e.beHurtByNum(self,damageInfo,this,damage,() => {
                            self.Manger.Record.pushActionRecord(e,hero,`由于`,`【${skill.name}】施加的${damageName}效果损失了${damage}兵力(${e.Arms})`,1);
                        });
                    }
                }, this, self, 'debuff');
                e.addHook('受伤时','受到持续性伤害时概率发动策略攻击',(attacker, damageInfo) => {
                    
                    if((damageInfo.type == 3 || damageInfo.type == 4) && self.Storage[this.id].hero[e.BattleCamp+e.Posname+e.Name] != true){
                        //必须在执行伤害前添加标记 否则会有问题
                        self.Storage[this.id].hero[e.BattleCamp+e.Posname+e.Name] = true;
                        self.Manger.Record.pushRecord(self,`的【${this.name}】当前生效几率为${self.getRealSkillRate(50)}%`,1)
                        if(getRandomBool(self.getRealSkillRate(50))){
                            self.Manger.Record.pushActionRecord(self, self, '执行来自', `的【${this.name}】效果`,1);
                            e.beHurt(self,{
                                type: 2,
                                rate: value
                            });
                            // console.log('debug',damageInfo,self.Storage[this.id].hero,e.BattleCamp+e.Posname+e.Name,222);
                        }else{
                            self.Manger.Record.pushRecord(self,`的【${this.name}】效果没有生效`,1)
                        }
                    }
                },this,self,'debuff');
            });

            self.addHook("回合开始时", "清除标记", () => {
                self.Storage[this.id].hero = {};
            }, this, self);
        }
    },

    {
        id: 1031, //ID
        name: "胜兵求战", //名称
        desc: "战斗中，每当自身发动需要准备的主战法时，有80.0%的几率跳过1回合的准备时间。同时，任意友军发动主动战法后，自身下一次主动战法造成的伤害提高15.0%，此效果最多叠加3次", //描述
        level: "B", //战法级别
        type: 1, //战法类型 1=指挥 2=主动 3=追击 4=被动
        target: 1, //目标数量
        target_type: "self", //作用目标
        limit: 2, //战法距离
        rate: "--", //战法发动率
        study: true, //战法是否可学习
        callskill: function (self) {
            self.Manger.Record.pushRecord(self, `发动【${this.name}】`);
            self.addHook("开始准备战法时","跳过准备回合",(skill) => {
                let realrate = self.getRealSkillRate(80);
                self.Manger.Record.pushActionRecord(self,self,`来自`,`的【${this.name}】当前生效几率为${realrate}%`);
                let skip = getRandomBool(realrate);
                if(skip){
                    self.READY_VAL[skill.id] = {
                        skip: -1,
                        from: this
                    };
                    self.Manger.Record.pushActionRecord(self,self,`【${this.name}】使`,"主战法减少准备1回合");
                }else{
                    self.Manger.Record.pushActionRecord(self,self,`来自`,`的【${this.name}】没有生效`);
                }
            },this,self);
        }
    },

    {
        id: 1032,
        name: "深谋远虑",
        desc: "使自身进行策略攻击的伤害提高11.0%，此效果每回合开始时额外叠加一次，持续直到战斗结束",
        level: "A",
        type: 4,
        target: 1,
        target_type: "self",
        limit: 0,
        rate: "--",
        study: true,
        callskill: function (self) {
            let value = 11;
            if (self.Manger.Round == "-1") {
                self.Manger.Record.pushRecord(self, `的战法【${this.name}】生效`);
                self.addState("inteDamageAdd", value, -1, this, self, true)
                let subskill = () => {
                    self.addState("inteDamageAdd", value, -1, this, self, true)
                }

                self.addHook("回合开始时", "回合开始时添加策略伤害提高", subskill, this, self);
            }
        }
    },
]