/**
 * 战斗管理器
 */

import { BattleHero } from "./battleHero";
import { Record } from './record';

const BattleRound = [-1, 0, 1, 2, 3, 4, 5, 6, 7, 8];

export class BattleManger {
    constructor(heroBattleTeam){
        this.BattleHeros = [];
        //战报详情
        this.Record = new Record(this);

        this.Round = null;

        //因为某一方的大营阵亡使战斗结束
        this.Over = false;

        this.BlueTeam = {
            hero:[],
            arms:0,
            hurtArms:0,
            total:0
        };

        this.RedTeam = {
            hero:[],
            arms:0,
            hurtArms:0,
            total:0
        };

        this.SortSpdHeros = [];
        let redindex = heroBattleTeam.blue.heros.length + heroBattleTeam.red.heros.length;
        let blueindex = 1;
        heroBattleTeam.blue.heros.forEach((e,i) => {
            let Posname = '';
            if(heroBattleTeam.blue.heros.length == 2){
                switch (i) {
                    case 0:
                        Posname = '大营';
                    break;
                    case 1:
                        Posname = '中军';
                    break;
                }
            }else if(heroBattleTeam.blue.heros.length == 3){
                switch (i) {
                    case 0:
                        Posname = '大营';
                    break;
                    case 1:
                        Posname = '中军';
                    break;
                    case 2:
                        Posname = '前锋';
                    break;
                }
            }else{
                Posname = '大营'
            }

            let hero = new BattleHero(e,'blue',this,heroBattleTeam.blue.morale);
            hero.Posname = Posname;
            hero.Index = blueindex;
            // hero.Name = hero.Name+hero.Index;
            hero.Name = hero.Name;
            blueindex++;
            this.BattleHeros.push(hero);
            this.BlueTeam.hero.push(hero);
            this.BlueTeam.total += hero.Arms;
        });

        heroBattleTeam.red.heros.forEach((e,i) => {
            let Posname = '';
            if(heroBattleTeam.red.heros.length == 2){
                switch (i) {
                    case 0:
                        Posname = '大营';
                    break;
                    case 1:
                        Posname = '中军';
                    break;
                }
            }else if(heroBattleTeam.red.heros.length == 3){
                switch (i) {
                    case 0:
                        Posname = '大营';
                    break;
                    case 1:
                        Posname = '中军';
                    break;
                    case 2:
                        Posname = '前锋';
                    break;
                }
            }else{
                Posname = '大营'
            }

            let hero = new BattleHero(e,'red',this,heroBattleTeam.red.morale);
            hero.Posname = Posname;
            hero.Index = redindex;
            // hero.Name = hero.Name+hero.Index;
            hero.Name = hero.Name;
            redindex--;
            this.BattleHeros.push(hero);
            this.RedTeam.hero.push(hero);
            this.RedTeam.total += hero.Arms;
        });

        this.startBattle();
        this.exitBattle();
    }

    startBattle(){
        let blueTeam = [];
        let redTeam = [];

        this.Record.pushSysRecord('【攻方阵容】');
        [...this.BlueTeam.hero].forEach(e=>{
            this.Record.pushSysRecord(`(${e.Posname}) 【${e.Name}】 ${e.Level}级 兵力：${e.Arms}`,1);
        })
        this.Record.pushSysRecord('【守方阵容】');
        [...this.RedTeam.hero].forEach(e=>{
            this.Record.pushSysRecord(`(${e.Posname}) 【${e.Name}】 ${e.Level}级 兵力：${e.Arms}`,1);
        })

        for (let index = 0; index < BattleRound.length; index++) {
            if(this.Over)break;
            this.Round = BattleRound[index];
            let roundName = this.Round == 0 ? '准备' : '第'+this.Round;
            if(index != 0) this.Record.pushRoundRecord(`${roundName}回合`);

            this.sortBattleHerosBySpd();

            // console.log(`${roundName}回合 速度排序`,this.SortSpdHeros);

            // 先执行回合开始时的效果
            this.SortSpdHeros.forEach((e) => {
                if(e.Arms > 0 && this.Round > 0){
                    e.callHook("回合开始时");
                }
            });

            this.SortSpdHeros.forEach((e) => {
                // 清除已攻击次数
                e.State.attackNum = 0;
                if(e.Arms > 0){
                    if(this.Round == '-1'){
                        // 执行被动
                        e.callPassiveSkill();
                    }else if(this.Round == '0'){
                        // 执行指挥
                        e.callCommandSkill()
                    }else{
                        if(this.Over)return;
                        e.Manger.Record.pushHeroRoundStart(e);
                        e.clearStateRounds();
                        e.callHook("行动前");
                        this.Record.pushRecord(e,"行动开始");
                        e.callHook("行动时");
                        // 判断混乱
                        if(e.isConfusion()){
                            e.skipRoundByConfusion();
                            return
                        }
                        // 执行主动
                        e.callActiveSkill();
                        if(this.Over)return;

                        // 判断怯战
                        if(e.isAttackLimit()){
                            e.skipAttackByAttackLimit();
                        }else{
                            // 执行普攻
                            e.callHook("普攻前");
                            e.callAttack();
                            if(this.Over)return;
                        }
                    }
                    // e.ActionRecord.forEach(r => {
                    //     this.pushRecord(r.msg,r.sub);
                    // });
                }else{
                    // this.pushRecord(`[${e.Name}] 由于阵亡 跳过回合`);
                }
            });

            if(this.Round == 0){
                [...this.BlueTeam.hero].forEach(e=>{
                    this.Record.pushRecord(e,`攻击(${e.Attrs.atk}) 谋略(${e.Attrs.int}) 防御(${e.Attrs.def}) 速度(${e.Attrs.spd})`);
                });
                [...this.RedTeam.hero].forEach(e=>{
                    this.Record.pushRecord(e,`攻击(${e.Attrs.atk}) 谋略(${e.Attrs.int}) 防御(${e.Attrs.def}) 速度(${e.Attrs.spd})`);
                });
            }

            if(this.Round >= 1){
                // 每个正式回合结束递减伤兵
                this.BattleHeros.forEach(e => {
                    if(e.Arms > 0 && e.HurtArms > 0){
                        console.log(e.Name,this.Round,'递减伤兵',Math.floor(e.HurtArms * 0.87));
                        e.HurtArms = Math.floor(e.HurtArms * 0.87);
                    }
                })
            }
        }
    }

    exitBattle(){
        this.BlueTeam.hero.forEach((e) => {
            this.BlueTeam.arms += e.Arms;
            this.BlueTeam.hurtArms += e.HurtArms;
        });

        this.RedTeam.hero.forEach((e) => {
            this.RedTeam.arms += e.Arms;
            this.RedTeam.hurtArms += e.HurtArms;
        });
    }

    pushRecord(msg,sub=[]){
        this.Record.push({msg,sub});
    }

    //根据速度对武将进行排序
    sortBattleHerosBySpd(){
        this.SortSpdHeros = [];

        [...[...this.BlueTeam.hero],...[...this.RedTeam.hero]].forEach((e) => {
            this.SortSpdHeros.push(e);
        })

        this.SortSpdHeros.sort((a,b)=>{
            return b.Attrs.spd - a.Attrs.spd;
        })

        //先手排序
        this.SortSpdHeros.sort((a,b)=>{
            let af = a.State.firstAction.rounds > 0;
            let bf = b.State.firstAction.rounds > 0;
            if(af == true && bf == false && this.Round <= a.State.firstAction.rounds){
                return -1;
            }else if(af == false && bf == true && this.Round <= b.State.firstAction.rounds){
                return 1;
            }
            return 0;
        })
    }
}