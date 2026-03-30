export function keepTwoDecimal(num) {
    var result = parseFloat(num);
    if (isNaN(result)) {
        return num;
    }
    result = Math.round(num * 100) / 100;
    return result;
};

export function makeSkillTag(self,skill,text=""){
    return `${self.BattleCamp}${self.Camp}-${self.Id}${self.Name}${skill.name}${text}`;
}

// 八舍九入
export function roundEightNine(num) {
    // 获取小数部分
    let decimalPart = num % 1;

    // 如果小数部分大于或等于 0.9，则进位
    if (decimalPart >= 0.9) {
        return Math.ceil(num);
    } else {
        // 否则舍去小数部分
        return Math.floor(num);
    }
}