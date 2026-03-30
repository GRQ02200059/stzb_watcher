var Common = (function (window, document) {
    //var HOST=window.location.href.indexOf('test') > -1 ? "https://g0.gph.netease.com/ngsocial/community/stzb/cfg/" : "https://stzb.163.com";
    var HOST="https://g0.gph.netease.com/ngsocial/community/stzb/cfg";
    //var HOST = "https://test.nie.163.com/test_cdn/stzb/pc/gw/20230821163204/json"; //本地
    var getJson = function (url, callback) {
        $.ajax({
            url: "//" + HOST.split("//")[1] + url,
            type: "get",
            dataType: "json",
            success: function (data) {
                callback && callback(data);
            },
        });
    };
    function init() {
        addEvent();
        //share();
    }

    function addEvent() {
        $(".nav-sjxq,.nav-sj").mouseenter(function () {
            $(".nav-sj").addClass("show");
        });
        $(".nav-sj,.nav-sjxq").mouseleave(function () {
            $(".nav-sj").removeClass("show");
        });
        var $img_url = $(".zhanbao-box .img-box img");
        if ($img_url.length > 0 && $img_url[0].src == "") {
            $(".zhanbao-box .img-box img").attr(
                "src",
                "../../img/zb_banner.jpg"
            );
        }
        var $introBox = $(".logo-desc-more");
        //公众号
        $(".public-item").hover(
            function () {
                var index = $(this).index() - 1;
                $(".public-show").eq(index).show();
            },
            function () {
                $(".public-show").hide();
            }
        );
        //游戏介绍
        $(".btn-intro").hover(
            function () {
                $introBox.addClass("show");
            },
            function () {
                $introBox.removeClass("show");
            }
        );
        //回到顶部
        var $topBtn = $("#btn-top");
        $(window).scroll(function () {
            var scrollTop = $(window).scrollTop();
            if (scrollTop > 800) {
                $topBtn.css("opacity", "1").show();
            } else {
                $topBtn.css("opacity", "0").hide();
            }
        });
        $topBtn.click(function () {
            $("html,body").animate(
                {
                    scrollTop: 0,
                },
                800
            );
        });

        //弹窗
        popEvent();
        //激活礼包
        activeGift();
        //领取礼包
        getGift();

        //头部
        $(".btn-intro").hover(
            function () {
                $(".logo-desc-more").fadeIn();
            },
            function () {
                $(".logo-desc-more").fadeOut();
            }
        );

        //nie.config.copyRight.setWhite();

        $(".nav a").removeClass("current");
        $(".nav a").each(function () {
            if (window.location.href.match($(this).attr("href")) != null) {
                $(this).addClass("current");
            }
        });

        //短信下载
        msgDownload();

        //礼包
        $(".Jlibao").click(function () {
            showPopBox(0);
        });
    }

    function share() {
        //统一引用分享信息
        var shareImg = $("#share_pic").attr("src");
        var shareDesc = $("#share_desc").html();
        var shareTitle = $("#share_title").html();
        var shareV5 = nie.require("nie.util.shareV5"),
            share = shareV5({
                fat: "#NIE-share",
                type: 1,
                defShow: [23, 22, 2, 1],
                title: shareTitle,
                img: shareImg,
                content: shareDesc,
            });
    }

    // 添加enter 事件
    function addEnter(id,callback) {
        // 获取 DOM 元素
        const searchInput = document.querySelector(id);
        let isComposing = false; // 跟踪输入法状态
        // 输入法开始事件
        searchInput.addEventListener('compositionstart', () => {
          isComposing = true;
        });
        // 输入法结束事件
        searchInput.addEventListener('compositionend', () => {
          isComposing = false;
        });
        // 键盘事件监听
        searchInput.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' && !isComposing) {
            e.preventDefault();
            callback(); 
          }
        });
    }

    //NCR转字符
    function getNCRtoString(str) {
        var newString = "",
            strArr;
        if (str != null && str != -1) {
            strArr = str.split(";");
            for (var i = 0, len = strArr.length - 1; i < len; i++) {
                newString += String.fromCharCode(
                    parseInt(strArr[i].replace("&#", ""), 10).toString()
                );
            }
            return newString;
        }
        return "";
    }
    //武将库数据处理，根据国家进行分类
    function dataHandle(data) {
        var category = [],
            i,
            len;

        category[0] = new Array(); //汉
        category[1] = new Array(); //蜀
        category[2] = new Array(); //魏
        category[3] = new Array(); //吴
        category[4] = new Array(); //群

        //分类
        for (i = 0, len = data.length; i < len; i++) {
            switch (data[i].country) {
                case "汉":
                    category[0].push(data[i]);
                    break;
                case "蜀":
                    category[1].push(data[i]);
                    break;
                case "魏":
                    category[2].push(data[i]);
                    break;
                case "吴":
                    category[3].push(data[i]);
                    break;
                case "群":
                    category[4].push(data[i]);
                    break;
            }
        }
        return category;
    }
    //技能库数据处理，根据战法进行分类
    function dataJinengHandle(data) {
        var category = [],
            i,
            len;

        category[0] = new Array(); //指挥
        category[1] = new Array(); //主动
        category[2] = new Array(); //被动
        category[3] = new Array(); //追击

        //分类
        for (i = 0, len = data.length; i < len; i++) {
            switch (data[i].type) {
                case "指挥":
                    category[0].push(data[i]);
                    break;
                case "主动":
                    category[1].push(data[i]);
                    break;
                case "被动":
                    category[2].push(data[i]);
                    break;
                case "追击":
                    category[3].push(data[i]);
                    break;
            }
        }
        return category;
    }
    //宝物库数据处理，根据类型进行分类
    function dataBaoWuHandle(data) {
        var category = [],
            i,
            len;

        category[0] = new Array(); //弓
        category[1] = new Array(); //长兵
        category[2] = new Array(); //刀
        category[3] = new Array(); //剑
        category[4] = new Array(); //扇
        category[5] = new Array(); //其他

        //分类
        for (i = 0, len = data.length; i < len; i++) {
            switch (data[i].type) {
                case "弓":
                    category[0].push(data[i]);
                    break;
                case "长兵":
                    category[1].push(data[i]);
                    break;
                case "刀":
                    category[2].push(data[i]);
                    break;
                case "剑":
                    category[3].push(data[i]);
                    break;
                case "扇":
                    category[4].push(data[i]);
                    break;
                case "其他":
                    category[5].push(data[i]);
                    break;
            }
        }
        return category;
    }
    //

    function getEveryBlockHtml(data) {
        var str = "",
            len = data.length > 17 ? 17 : data.length,
            i;
        str += '<div class="slide-item"><ul class="pic-list clearfix">';
        for (i = 0; i < len; i++) {
            str +=
                '<li><a href="#" target="_blank"><img src="https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_' +
                data[i].iconId +
                '.jpg?gameid=g10" alt="' +
                data[i].name +
                '" width="80" height="80"><em>' +
                data[i].name +
                "</em><i></i></a></li>";
        }
        if (len == 17) {
            str +=
                '<li class="lib-more"><a href="#" target="_blank">更多</a></li>';
        }
        str += "</div>";
        return str;
    }

    function popEvent() {
        var $mask = $("#mask"),
            $popBox = $("#pop-box"),
            $popTg = $("#pop-tg");
        $(".pop-th a").click(function () {
            var $me = $(this);
            $(".pop-th a").removeClass("current");
            $me.addClass("current");
            $(".pop-bd").hide();
            $(".pop-bd").eq($me.index()).show();
        });
        $(".tg-btn").click(function () {
            $mask.fadeIn();
            $popTg.fadeIn();
        });
        $(".pop-close").click(function () {
            $mask.fadeOut();
            $popBox.fadeOut();
            $popTg.fadeOut();
        });
        // $('.Jyuyue').click(function() {
        //     showPopBox(0);
        // });
    }

    function showPopBox(index) {
        var $mask = $("#mask"),
            $popBox = $("#pop-box");
        $("#gift-phone").val("请输入您的手机号码");
        $("#jihuo-id").val($("#jihuo-id").attr("data-placeholder"));
        $("#jihuo-gift").val($("#jihuo-gift").attr("data-placeholder"));
        $mask.fadeIn();
        $popBox.fadeIn();
        $(".pop-th a").eq(index).click();
    }

    function inputEvent() {
        var $inputList = $("#pop-box input");
        $inputList.focus(function () {
            var $me = $(this);
            if ($me.val() == $me.attr("data-placeholder")) {
                $me.val("");
            }
        });
        $inputList.blur(function () {
            var $me = $(this);
            if ($me.val() == "") {
                $me.val($me.attr("data-placeholder"));
            } else {
                $me.parent().removeClass("pop-err");
            }
        });
    }

    function verification($input) {
        var result = true;
        $input.each(function () {
            var $me = $(this);
            if ($me.val() == "" || $me.val() == $me.attr("data-placeholder")) {
                $me.next(".pop-error-txt").html($me.attr("data-placeholder"));
                $me.parent().addClass("pop-err");
                result = false;
            }
        });
        return result;
    }

    function activeGift() {
        var $player_id = $("#jihuo-id"),
            $gift_code = $("#jihuo-gift");

        inputEvent();

        $("#submit-jihuo").click(function () {
            if (verification($("#active-box input"))) {
                $(".pop-row").removeClass("pop-err");
                $.ajax({
                    url: "https://gifter.webapp.163.com/g10/active_cdkey_game",
                    data: {
                        userid: $player_id.val(),
                        code: $gift_code.val(),
                        device: "IOS",
                    },
                    type: "get",
                    dataType: "jsonp",
                    success: function (data) {
                        if (data.status) {
                            alert("恭喜，激活成功！");
                            $player_id.val($player_id.attr("data-placeholder"));
                            $gift_code.val($gift_code.attr("data-placeholder"));
                        } else {
                            alert(data.msg);
                            $player_id.val($player_id.attr("data-placeholder"));
                            $gift_code.val($gift_code.attr("data-placeholder"));
                        }
                    },
                });
            }
        });

        $(".tips-id").hover(
            function () {
                $("#tips-id-img").fadeIn();
            },
            function () {
                $("#tips-id-img").fadeOut();
            }
        );
    }
    //预约，拷贝线上的
    function yuyue() {
        //弹出预约窗口
        var finishvote = 0; //是否完成投票
        //-----------以下为普通弹层示例
        $(".Jyuyue").click(function () {
            openD({
                id: ".pop_book", //弹出的弹层id
                type: "1", //1为普通弹层，2为flash弹层，3为视频弹层
            });
            if (finishvote == 0) {
            } else {
                $(".popcontentseven").hide();
                $(".popcontentone").show();
                $(".vote_iframe").attr("src", "");
                finishvote = 0;
            }
        });

        //弹层
        //--这个函数是将三种基本弹层合在一体的，所以代码会比较长一些，可以根据需求自己独立出来相对应的弹层代码
        function openD(opt) {
            //只针对一个tab con
            var settings = {
                    id: "",
                    type: "", //1为普通弹层，2为flash弹层，3为视频弹层
                },
                opt = opt || {};
            settings = $.extend(settings, opt);

            var popbg = $("#NIE-overlayer-book"),
                popbg2 = $(".topoverlayer"),
                popbg3 = $(".bottomoverlayer"),
                popid = $(settings.id),
                type = settings.type,
                w = parseInt(settings.width),
                h = parseInt(settings.height),
                vimg = settings.startImg,
                furl = settings.flashurl,
                vurl = settings.videourl,
                wmode = settings.wmode,
                dh = $(document).height(),
                wh = $(window).height(),
                ww = $(window).width(),
                st = ($(window).height() - 500) / 2 + "px",
                sl = $(window).scrollLeft();
            // 蒙版弹出
            popbg
                .css({
                    height: dh,
                })
                .show();
            // 弹层弹出
            popbg2.show();
            popbg3.show();

            function posPop(idname) {
                idname.height() > wh
                    ? idname.fadeIn().css({
                          top: 80,
                          left: (ww - idname.width()) / 2,
                      })
                    : idname.fadeIn().css({
                          top: 80,
                          left: (ww - idname.outerWidth()) / 2,
                      });
            }
            //  弹层关闭
            $(".aCloseQ").click(function () {
                $(this)
                    .parent()
                    .fadeOut(function () {
                        popbg.hide();
                        popbg2.hide();
                        popbg3.hide();
                    });
            });

            //判断弹层类别
            switch (type) {
                case "1":
                    posPop(popid);
                    break;
                default:
                    break;
            }
        }
        //调整弹层窗口位置
        $(window).bind("resize", function () {
            // 对礼包详情弹窗的重新定位
            if ($(".pop_book").is(":visible")) {
                $(".pop_book").css({
                    left:
                        ($(window).width() > 1280 ? $(window).width() : 1280) /
                            2 -
                        522 +
                        "px",
                    top: 80 + "px",
                });
                // 对视频弹窗进行重定位
            }
            if ($(".pop_news").is(":visible")) {
                $(".pop_news").css({
                    left:
                        ($(window).width() > 1280 ? $(window).width() : 1280) /
                            2 -
                        350 +
                        "px",
                    top: 80 + "px",
                });
                // 对视频弹窗进行重定位
            }
        });

        //手机号预约部分

        var device_type;
        var phone_number;
        //判断是否是手机号
        var is_phone = function (phone) {
            return phone.match(
                /^13\d{9}$|^14\d{9}$|^15\d{9}$|^18\d{9}$|^16\d{9}$|^17\d{9}$/
            );
        }; // 选择机型
        $(".device_select").click(function () {
            $(".device_select").removeClass("current");
            $(this).addClass("current");
            $("#" + $(this).attr("data")).attr("checked", "checked");
        });
        // $('.device_select.device1').click(function(){
        //     alert("【安卓封测，请移步下载】");
        // });

        //判断信息完整 并发送请求
        $(".submit").click(function () {
            device_type = $("input[name='device']:checked").attr("value");
            phone_number = $("#txtMobile").val();

            if (device_type == undefined) {
                alert("请选择您的系统");
                return false;
            } else if (phone_number.length != 11) {
                alert("请输入完整的号码");
                return false;
            } else if (!is_phone(phone_number)) {
                alert("你的手机号码格式错误");
                return false;
            } else {
                $.ajax({
                    url:
                        "https://mobile-game-appoint.webapp.163.com/appoint/g10-ios/" +
                        phone_number +
                        "/" +
                        device_type +
                        "/",
                    type: "get",
                    dataType: "jsonp",
                    jsonpCallback: "cardlist",
                    success: function (data) {
                        if (data.status == "ok") {
                            $(".popcontentone").fadeOut();
                            $(".pop_slide").fadeIn(function () {
                                $(".aLeft").fadeIn();
                                $(".aRight").fadeIn();
                                $(".progress_bar").fadeIn();
                            });
                        } else {
                            alert(data.status);
                        }
                    },
                });
            }
        });

        //投票系统 玩家填写内容
        $(".done").click(function () {
            //投票校验
            var isChe = true,
                isPassed = true,
                option = [];
            $(".question").each(function () {
                // console.log($(this).data("index"));
                var index = $(this).data("index") + 1;
                if (!isChe) {
                    return;
                }
                if ($(this).find("input:checked").length == 0) {
                    isChe = false;
                    isPassed = false;
                    alert("请填写第" + index + "道题！");
                    return;
                }
                if (isChe) {
                    var que_index = $(this).data("index"),
                        i = 0;
                    option[que_index] = [];

                    $(this)
                        .find("input:checked")
                        .each(function () {
                            var que_index_che =
                                ($(this).parent("label").index() + 1) / 2;

                            option[que_index][i] = que_index_che;
                            i++;
                        });
                }
            });
            if (isPassed) {
                var link_str = "";
                $.each(option, function (i, val) {
                    link_str += val + "&";
                });
                $(".vote_iframe").attr(
                    "src",
                    "https://cgi.mmog.163.com:8088/v4a/show_vote/1193/?" +
                        link_str
                );
                $(".aLeft").fadeOut();
                $(".aRight").fadeOut();
                $(".progress_bar").fadeOut();
                $(".pop_slide").fadeOut();
                $(".aRight").trigger("click");
                $(".popcontentseven").fadeIn();
                finishvote = 1;
                $("input").removeAttr("checked");
                $(".device_select ").removeClass("current");
                $("#txtMobile").val("");
            }

            //投票成功 下级动画
        });
        //左右切换

        $.slides({
            paneParent: $(".pop_slide"),
            btnPrev: $(".aLeft"),
            btnNext: $(".aRight"),
        });
        //进度条进度切换
        function progress(index) {
            $(".line").removeClass("lineon");
            $(".progress").removeClass("progresson");
            for (i = 1; i <= index; i++) {
                $(".line" + i).addClass("lineon");
                $(".block" + i).addClass("progresson");
            }
            if (index == 5) $(".line6").addClass("lineon");
        }
        var current = 1;
        var bindclickL = false;
        $(".aLeft").click(function () {
            if (bindclickL == false) {
                bindclickL = true;
                current = current - 1;
                if (current <= 0) current = 5;
                progress(current);
                setTimeout(function () {
                    bindclickL = false;
                }, 300);
            } else {
                return false;
            }
        });
        var bindclickR = false;
        $(".aRight").click(function () {
            if (bindclickR == false) {
                bindclickR = true;
                current = current + 1;
                if (current >= 6) current = 1;
                progress(current);
                setTimeout(function () {
                    bindclickR = false;
                }, 300);
            } else {
                return false;
            }
        });
    }

    function getQueryString(name) {
        var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
        var r = window.location.search.substr(1).match(reg);
        if (r != null) return unescape(r[2]);
        return null;
    }
    //短信下载
    function msgDownload() {
        var $mobile = $("#msg-input"),
            $code = $("#code"),
            $captcha = $("#code_img");
        $mobile.focus(function () {
            var $me = $(this);
            if ($me.val() == $me.attr("data-placeholder")) {
                $me.val("");
            }
            $captcha.show();
        });
        $mobile.blur(function () {
            var $me = $(this);
            if ($me.val() == "") {
                $me.val($me.attr("data-placeholder"));
            }
            //$captcha.hide();
        });
        $code.focus(function () {
            var $me = $(this);
            if ($me.val() == "输入验证码") {
                $me.val("");
            }
        });
        $code.blur(function () {
            var $me = $(this);
            if ($me.val() == "") {
                $me.val("输入验证码");
            }
        });
        $(".msg-download").click(function () {
            var phone = $mobile.val(),
                code = $code.val();
            if (phone == "" || phone == $mobile.attr("data-placeholder")) {
                alert("手机号不能为空");
            } else if (!/^1(3|4|5|7|8)\d{9}$/.test(phone)) {
                alert("手机号码不合法");
            } else if (code == "" || code == "输入验证码") {
                alert("验证码不能为空");
            } else {
                $.ajax({
                    url:
                        "https://sms-down.webapp.163.com/app/send-sms/?phone=" +
                        phone +
                        "&message=stzb&captcha_ans=" +
                        code,
                    type: "get",
                    dataType: "jsonp",
                    success: function (data) {
                        if (data.status == "ok") {
                            $("#code_img img").click();
                            $mobile.val($mobile.attr("data-placeholder"));
                            $code.val("输入验证码");
                            $captcha.hide();
                            alert(data.msg);
                        } else {
                            alert(data.msg);
                        }
                    },
                });
            }
        });

        var timerChkcode;
        // 点击验证码图片更换图片
        function getNewChkcodeImg() {
            return (
                "https://sms-down.webapp.163.com/app/captcha/?" + Math.random()
            );
        }
        $("#code_img img").click(function () {
            var $me = $(this);
            if (timerChkcode) {
                timerChkcode = clearTimeout(timerChkcode);
            }
            timerChkcode = setTimeout(function () {
                $me.attr("src", getNewChkcodeImg());
            }, 100);
        });
    }
    //领取礼包
    function getGift() {
        var $mobile = $("#gift-phone"),
            $giftCabinet = $(".gift-cabinet"),
            $giftBox = $(".gift-box"),
            $mask = $("#mask");

        $("#btn-gift-submit").click(function () {
            var phone = $mobile.val(),
                device;
            if (phone == "" || phone == "请输入您的手机号码") {
                alert("手机号不能为空");
            } else if (!/^1(3|4|5|7|8)\d{9}$/.test(phone)) {
                alert("手机号码不合法");
            } else {
                $.ajax({
                    url: "https://gifter.webapp.163.com/g10/get_cdkey_by_sms",
                    type: "get",
                    dataType: "jsonp",
                    data: {
                        mobile: phone,
                    },
                    success: function (data) {
                        //console.log(data)
                        if (data.status && data.msg == "ok") {
                            $mobile.val("请输入您的手机号码");
                            //showPopBox(1);
                            alert("恭喜！领取成功！");
                            $(".pop-close").click();
                        } else {
                            $mobile.val("请输入您的手机号码");
                            alert(data.msg);
                        }
                    },
                });
            }
        });
    }

    return {
        init: init,
        dataHandle: dataHandle,
        yuyue: yuyue,
        showPopBox: showPopBox,
        dataJinengHandle: dataJinengHandle,
        dataBaoWuHandle: dataBaoWuHandle,
        getNCRtoString: getNCRtoString,
        getQueryString: getQueryString,
        getJson: getJson,
        addEnter: addEnter,
        getHost: function () {
            return HOST;
        },
    };
})(window, document, undefined);

Common.init();

;nie.define("sharetest",function(){{var e=$("#share_pic").attr("src"),t=$("#share_desc").html(),i=$("#share_title").html(),r=nie.require("nie.util.shareV5");r({fat:"#NIE-share",type:1,defShow:[23,22,2],title:i,img:e,content:t})}});
;var RoleList = (function () {
    var _category = [],
        _role_data = [],
        _isPrev = false,
        _start = 0,
        _isNext = false,
        _research_data = [],
        jineng_data = {};

    init();

    function init() {
        getAllRoleData();
        addEvent();
    }

    function addEvent() {
        sideNavEvent($(".nav-list-01 a"));
        sideNavEvent($(".nav-list-02 a"));
        sideNavEvent($(".nav-list-03 a"));

        reserchEvent();
    }

    //左方导航
    function sideNavEvent($navList) {
        $navList.click(function () {
            var quality, contory, cost;
            //交互
            $navList.parent().removeClass("current");
            $(this).parent().addClass("current");
            //获取选择的类别
            quality =
                $(".nav-list-01 .current").length > 0
                    ? $(".nav-list-01 .current").attr("data-quality")
                    : "all";
            contory =
                $(".nav-list-02 .current").length > 0
                    ? $(".nav-list-02 .current").attr("data-contory")
                    : "all";
            cost =
                $(".nav-list-03 .current").length > 0
                    ? $(".nav-list-03 .current").attr("data-cost")
                    : "all";
            //匹配
            _research_data = researchData(
                {
                    quality: quality,
                    contory: contory,
                    cost: cost,
                },
                _role_data
            );
            getResultHtml(
                getRoleDataFromPage(_research_data, 0),
                _research_data
            );
        });
    }
    //获取武将库信息
    function getAllRoleData() {
        var quality = Common.getQueryString("quality"),
            contory = Common.getQueryString("contory"),
            cost = Common.getQueryString("cost");
        // getJson('/json/jineng_list.json', function(data){
        //     for(var i = 0, len = data.length; i < len; i++){
        //         jineng_data[data[i].id] = data[i].type;  //战法类型
        //     }
        Common.getJson("/hero_extra.json?gameid=g10", function (data) {
            console.log(data);
            var name = getQueryString("search");
            const idsToRemove = [100806, 100807, 100808];
            _role_data = data.filter(item => !idsToRemove.includes(item.id));
            // _role_data = data;
            _category = Common.dataHandle(data); //分类
            //武将列表页才初始显示
            if (name != null) {
                $("#research-input").val(name);
                research(name);
            } else if ($("#card_list").length > 0) {
                getResultHtml(getRoleDataFromPage(_role_data, 0), _role_data);
            }
            if (quality != null) {
                $(".nav-list-01 a").eq(quality).click();
            }
            if (contory != null) {
                $(".nav-list-02 a").eq(contory).click();
            }
            if (cost != null) {
                $(".nav-list-03 a").eq(cost).click();
            }
        });
      }
    //根据武将库分页
    function getRoleDataFromPage(data, start) {
        var result = null;
        _start = start;
        if (start == 0) {
            _isPrev = false;
        } else {
            _isPrev = true;
        }
        if (data.length - _start > 24) {
            _isNext = true;
        } else {
            _isNext = false;
        }
        if (data.length > 0) {
            result = data.slice(start, start + 24);
            _start += result.length;
        }
        return result;
    }
    //NCR转字符
    function getNCRtoString(str) {
        var newString = "",
            strArr,
            temp;
        console.log(str);
        console.log(strArr);
        if (str != null && str != -1) {
            strArr = str.split(";");
            for (var i = 0, len = strArr.length - 1; i < len; i++) {
                if (strArr[i].length > 6) {
                    temp = strArr[i].split("&#");
                    strArr[i] = temp[1];
                    newString += temp[0];
                }
                newString += String.fromCharCode(
                    parseInt(strArr[i].replace("&#", ""), 10).toString()
                );
            }
            return newString;
        }
        return "";
    }
    //重绘
    function getResultHtml(data, all_data) {
        var str = "",
            len,
            i,
            prev_html = "",
            next_html = "",
            under = [function(_template_object
/**/) {
var _template_fun_array=[];
var fn=(function(__data__){
var _template_varName='';
for(var name in __data__){
_template_varName+=('var '+name+'=__data__["'+name+'"];');
};
eval(_template_varName);
_template_fun_array.push('');    var len,i;    var jineng_type = ["", "指挥", "主动", "被动", "追击"];_template_fun_array.push('    ');if(data && data.length > 0){;_template_fun_array.push('        ');len = data.length;        for(i = 0; i < len; i++){            if(data[i].id != null){_template_fun_array.push('                <li>                    <img src="https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_small_',typeof(data[i].iconId) === 'undefined'?'':baidu.template._encodeHTML(data[i].iconId),'.jpg?gameid=g10" alt="',typeof(data[i].name) === 'undefined'?'':baidu.template._encodeHTML(data[i].name),'" width="100" height="100">                    <em>',typeof(data[i].name)==='undefined'?'':data[i].name,'</em>                    <i class="choose">                        <a target="_blank" href="herolist/',typeof(data[i].iconId) === 'undefined'?'':baidu.template._encodeHTML(data[i].iconId),'.html" title="',typeof(data[i].name) === 'undefined'?'':baidu.template._encodeHTML(data[i].name),'">查看</a>                        <a href="javascript:;" class="Jbtn_item_choose" data-type="wujiang" data-id="',typeof(data[i].iconId) === 'undefined'?'':baidu.template._encodeHTML(data[i].iconId),'" data-name="',typeof(data[i].name) === 'undefined'?'':baidu.template._encodeHTML(data[i].name),'" data-bing="',typeof(data[i].type) === 'undefined'?'':baidu.template._encodeHTML(data[i].type),'" data-zfid="',typeof(data[i].methodId) === 'undefined'?'':baidu.template._encodeHTML(data[i].methodId),'" data-zfname="',typeof(data[i].methodName) === 'undefined'?'':baidu.template._encodeHTML(data[i].methodName),'" data-zfjiengtype="',typeof(jineng_type.indexOf(jineng_data[data[i].methodId])) === 'undefined'?'':baidu.template._encodeHTML(jineng_type.indexOf(jineng_data[data[i].methodId])),'" data-cost="',typeof(data[i].cost) === 'undefined'?'':baidu.template._encodeHTML(data[i].cost),'">选取</a>                                            </i>                    <i class="no-choose">                        <a target="_blank" href="herolist/',typeof(data[i].id) === 'undefined'?'':baidu.template._encodeHTML(data[i].id),'.html">查看</a>                    </i>                </li>            ');}        }_template_fun_array.push('        <div class="pagination-box">        '); if(_isPrev){ _template_fun_array.push('            <div class="btn-page" id="btn_prev"><a href="javascript:;"></a></div>        ');}_template_fun_array.push('        '); if(_isNext){ _template_fun_array.push('            <div class="btn-page" id="btn_next"><a href="javascript:;" ></a></div>        ');}_template_fun_array.push('        </div>    ');}else{_template_fun_array.push('    <p class="role-error">没有找到相应的英雄...</p>');}_template_fun_array.push('');
_template_varName=null;
})(_template_object);
fn = null;
return _template_fun_array.join('');

}][0];

        str = under({
            data: data,
            _isPrev: _isPrev,
            _isNext: _isNext,
            jineng_data: jineng_data,
        });

        $("#role_list").html(str);
        console.log("str", data);

        $("#btn_next").click(function () {
            getResultHtml(getRoleDataFromPage(all_data, _start), all_data);
        });
        $("#btn_prev").click(function () {
            var s = _start - 48 > 0 ? _start - 48 : 0;
            getResultHtml(getRoleDataFromPage(all_data, s), all_data);
        });
        if ($("#neye-box").length > 0) {
            $("#neye-box").addClass("role-show-list");
        }
        $("#role_list").show();

        $("#role_list li").hover(
            function () {
                var $me = $(this);
                if ($me.parent().attr("data-choose") == 1) {
                    $me.find(".choose").addClass("show");
                } else {
                    $me.find(".no-choose").addClass("show");
                }
            },
            function () {
                $(this).find("i").removeClass("show");
            }
        );
    }
    //按类别出数据
    function researchData(config, data) {
        var result_data = [],
            i,
            len,
            temp_quality,
            temp_contory,
            temp_cost;

        //匹配的加入
        for (i = 0, len = data.length; i < len; i++) {
            temp_quality =
                config.quality == "all" ? data[i].quality : config.quality;
            temp_contory =
                config.contory == "all" ? data[i].country : config.contory;
            temp_cost = config.cost == "all" ? data[i].cost : config.cost;
            if (
                data[i].quality == temp_quality &&
                data[i].country == temp_contory &&
                data[i].cost == temp_cost
            ) {
                result_data.push(data[i]);
            }
        }

        return result_data;
    }
    function reserchEvent() {
        var $research = $("#research-input");
        $research.focus(function () {
            var $me = $(this);
            if ($me.val() == "请输入搜索内容：如武将名称") {
                $me.val("");
            }
        });
        $research.blur(function () {
            var $me = $(this);
            if ($me.val() == "") {
                $me.val("请输入搜索内容：如武将名称");
            }
        });
        $("#btn-research").click(function () {
            var value = $("#research-input").val();
            if (value == "" || value == "请输入搜索内容：如武将名称") {
                getAllRoleData();
                alert("请输入搜索内容：如武将名称");
            } else {
                research(value);
            }
        });
        Common.addEnter("#research-input",function(){
            $('#btn-research').click();
        })
    }
    //搜索
    function research(txt) {
        var result_data = [],
            i,
            len,
            data = _role_data;

        //匹配的加入
        for (i = 0, len = data.length; i < len; i++) {
            if (data[i].name.match(txt) != null) {
                result_data.push(data[i]);
            }
        }
        getResultHtml(getRoleDataFromPage(result_data, 0), result_data);
    }

    function getQueryString(name) {
        var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
        var r = window.location.search.substr(1).match(reg);
        if (r != null) return decodeURIComponent(r[2]);
        return null;
    }
})();

;nie.define("DB",function(){var n="//web-lib.webapp.163.com",i="//gnr-api.webapp.163.com",t=function(n,i,t,e){$.ajax({type:"get",url:n+i,dataType:"jsonp",data:t,timeout:5e3,success:function(n){e&&e(n)},error:function(){alert("\u670d\u52a1\u5668\u7e41\u5fd9\uff0c\u8bf7\u7a0d\u5019\u91cd\u8bd5")}})};return{sensitive_test:function(i,e){t(n,"/badword/api",i,e)},submit_group:function(n,e){t(i,"/api/g10simulation/signup",n,e)}}});
;nie.define(function(){function showPop(t){$(".Jpj_pop").hide(),$("#"+t).show(),$("#pop-mask").show(),setTimeout(function(){$("#pop-mask").addClass("show")},50)}function closePop(){$("#pop-mask").removeClass("show"),setTimeout(function(){$("#pop-mask").hide(),$(".Jpj_pop").hide()})}function addEvent(){$(".btn-new-close").click(function(){closePop(),$choose_box.attr("data-choose",0)})}function getQueryString(t){var a=new RegExp("(^|&)"+t+"=([^&]*)(&|$)","i"),e=window.location.search.substr(1).match(a);return null!=e?unescape(e[2]):null}function showPj(){Login.fn.isLogin(function(t){showPop(t?"pop_new":"pj_login")})}function chooseEvent(){$("#pj-heros").on("click",".Jbtn_choose",function(t){var a=$(this),e=$(t.currentTarget),n=e.parent().find(".Jbtn_choose").eq(0);a.attr("data-id")||("jineng_list.html"!=a.attr("data-type")||n.attr("data-id")?location.href.match(a.attr("data-type"))?($choose_target=e,$choose_box.attr("data-choose",1),e.attr("data-loc").match("0")||$(".nav-list-05 a").eq(bing_type.indexOf(n.attr("data-bing"))).click(),closePop()):(saveData($(this).attr("data-loc")),location.href=e.attr("data-loc").match("0")?a.attr("data-type")+"?from=pjmn":a.attr("data-type")+"?from=pjmn&soldierType="+bing_type.indexOf(n.attr("data-bing"))):alert("\u8bf7\u5148\u9009\u62e9\u6b66\u5c06"))}),$("body").on("click",".Jbtn_item_choose",function(e){var $me=$(this),img_url,$choose=$choose_target.parent().find(".Jbtn_choose"),under=[function(_template_object){var _template_fun_array=[],fn=function(__data__){var _template_varName="";for(var name in __data__)_template_varName+="var "+name+'=__data__["'+name+'"];';eval(_template_varName),_template_fun_array.push(""),"wujiang"==data.type?_template_fun_array.push('<img src="https://stzb.res.netease.com/pc/qt/20170323200251/data/small/card_',"undefined"==typeof data.iconId?"":baidu.template._encodeHTML(data.iconId),'.jpg?s=news"/><span class="name-txt">',"undefined"==typeof data.name?"":data.name,'</span><i class="btn-item-close Jitem_close"></i>'):(_template_fun_array.push('<img src="https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0',"undefined"==typeof data.bing?"":baidu.template._encodeHTML(data.bing),'.png"/><span class="name-txt">',"undefined"==typeof data.name?"":data.name,"</span>"),data.noClose||_template_fun_array.push('<i class="btn-item-close Jitem_close"></i>'),_template_fun_array.push("")),_template_fun_array.push(""),_template_varName=null}(_template_object);return fn=null,_template_fun_array.join("")}][0],bing=$me.attr("wujiang"==$me.attr("data-type")?"data-bing":"data-jinengtype"),data={type:$me.attr("data-type"),name:$me.attr("data-name"),id:$me.attr("data-id"),bing:bing};if(img_url=under({data:data}),"jineng"==$me.attr("data-type")){if(""==$me.attr("data-dismantling")&&!$me.attr("data-dismantling")&&!except.includes($me.attr("data-id")))return alert("\u8be5\u6280\u80fd\u4e0d\u80fd\u88ab\u88c5\u914d\u54e6\uff0c\u4e3b\u516c\uff01"),!1;if(!$me.attr("data-bing").match($choose.eq(0).attr("data-bing")))return alert("\u5175\u79cd\u4e0d\u7b26"),!1;if($me.attr("data-id")==$choose.eq(1).attr("data-id")||$me.attr("data-id")==$choose.eq(2).attr("data-id")||$me.attr("data-id")==$choose.eq(3).attr("data-id"))return alert("\u6218\u6cd5\u4e0d\u53ef\u91cd\u590d"),!1;if(!($('.Jchoose_skill[data-id="'+$me.attr("data-id")+'"]').length<$me.attr("data-skillcount")))return alert("\u8be5\u6280\u80fd\u5df2\u7ecf\u7531\u5176\u4ed6\u5c06\u9886\u4f7f\u7528\uff0c\u4e3b\u516c\u8bf7\u52ff\u91cd\u590d\u88c5\u914d"),!1;200810==$me.attr("data-id")&&($choose_target.attr("data-cost",-.5),cur_cost-=.5)}if("wujiang"==$me.attr("data-type")){if($me.attr("data-id")==$('.Jbtn_choose[data-loc="dy_0"]').attr("data-id")||$me.attr("data-id")==$('.Jbtn_choose[data-loc="zy_0"]').attr("data-id")||$me.attr("data-id")==$('.Jbtn_choose[data-loc="qf_0"]').attr("data-id"))return alert("\u6b66\u5c06\u4e0d\u53ef\u91cd\u590d"),!1;if(!(cur_cost+1*$me.attr("data-cost")<=10))return alert("cost\u4e0a\u9650\u4e0d\u80fd\u8d85\u8fc710\u54e6\uff0c\u4e3b\u516c\u8bf7\u8c03\u6574\u540e\u518d\u8bd5"),!1;cur_cost+=1*$me.attr("data-cost"),$choose_target.attr("data-cost",$me.attr("data-cost"))}$choose_target.attr("data-id",$me.attr("data-id")).attr("data-name",$me.attr("data-name")).attr("data-bing",bing).find(".pj-img-box").html(img_url),$me.attr("data-zfid")&&(img_url=under({data:{type:"jineng",id:$me.attr("data-zfid"),name:$me.attr("data-zfname"),bing:$me.attr("data-zfjiengtype"),noClose:!0}}),$choose_target.next().attr("data-id",$me.attr("data-zfid")).attr("data-name",$me.attr("data-zfname")).attr("data-bing",$me.attr("data-zfjiengtype")).find(".pj-img-box").html(img_url)),showPj(),console.log(cur_cost)}),$(".pj-img-box").on("click",".Jitem_close",function(t){var a=$(this),e=a.parents(".Jbtn_choose");t.stopPropagation(),e.removeAttr("data-id").removeAttr("data-name").removeAttr("data-bing"),a.parent().html(""),e.attr("data-loc").match("0")&&(cur_cost-=1*e.attr("data-cost"),e.parent().find(".btn-add-zf").each(function(){var t=$(this);t.attr("data-cost")&&(cur_cost-=1*t.attr("data-cost")),$(this).removeAttr("data-id").removeAttr("data-name").removeAttr("data-bing").find(".pj-img-box").html("")})),console.log(cur_cost)})}function getData(){var t={dy:[],zy:[],qf:[]};return $(".pj-heros-item").each(function(a){$(this).find(".Jbtn_choose").each(function(){var e=$(this);t[data_attr[a]].push(e.attr("data-id")?{id:e.attr("data-id"),name:e.attr("data-name"),bing:e.attr("data-bing"),cost:e.attr("data-cost")}:null)})}),t}function saveData(t){var a={tit:encodeURIComponent($("#pj_title").val()),desc:encodeURIComponent($("#pj_desc").val())};$.cookie("stzb_pjmn_data",JSON.stringify(getData())),$.cookie("stzb_pjmn_target",t),$.cookie("stzb_pjmn_info",JSON.stringify(a))}function decodeData(){var data=JSON.parse($.cookie("stzb_pjmn_data")),info=JSON.parse($.cookie("stzb_pjmn_info")),under=[function(_template_object){var _template_fun_array=[],fn=function(__data__){var _template_varName="";for(var name in __data__)_template_varName+="var "+name+'=__data__["'+name+'"];';eval(_template_varName),_template_fun_array.push("");var arr_txt=["\u5927\u8425","\u4e2d\u8425","\u524d\u950b"],zhanfa=["\u6218\u6cd5\u4e00","\u6218\u6cd5\u4e8c","\u6218\u6cd5\u4e09"];if(_template_fun_array.push(""),data){_template_fun_array.push("    ");for(var k=0,len2=data_attr.length;len2>k;k++){_template_fun_array.push('        <div class="pj-heros-item">             <em>                <i>',"undefined"==typeof arr_txt[k]?"":baidu.template._encodeHTML(arr_txt[k]),"</i>            </em>                            ");for(var i=0,len=data[data_attr[k]].length;len>i;i++)_template_fun_array.push("                    "),0==i?(_template_fun_array.push('                        <a href="javascript:;" class="btn-add-wj Jbtn_choose" data-loc="',"undefined"==typeof data_attr[k]?"":baidu.template._encodeHTML(data_attr[k]),"_","undefined"==typeof i?"":baidu.template._encodeHTML(i),'" data-type="card_list.html" '),data[data_attr[k]][i]&&_template_fun_array.push(' data-id="',"undefined"==typeof data[data_attr[k]][i].iconId?"":baidu.template._encodeHTML(data[data_attr[k]][i].iconId),'" data-name="',"undefined"==typeof data[data_attr[k]][i].name?"":baidu.template._encodeHTML(data[data_attr[k]][i].name),'" data-cost="',"undefined"==typeof data[data_attr[k]][i].cost?"":baidu.template._encodeHTML(data[data_attr[k]][i].cost),'" data-bing="',"undefined"==typeof data[data_attr[k]][i].bing?"":baidu.template._encodeHTML(data[data_attr[k]][i].bing),'"'),_template_fun_array.push('>                            \u70b9\u51fb\u6dfb\u52a0\u6b66\u5c06                            <span class="pj-img-box">                                '),data[data_attr[k]][i]&&_template_fun_array.push('                                    <img src="https://stzb.res.netease.com/pc/qt/20170323200251/data/small/card_',"undefined"==typeof data[data_attr[k]][i].iconId?"":baidu.template._encodeHTML(data[data_attr[k]][i].iconId),'.jpg"/>                                    <span class="name-txt">',"undefined"==typeof data[data_attr[k]][i].name?"":data[data_attr[k]][i].name,'</span>                                    <i class="btn-item-close Jitem_close"></i>                                '),_template_fun_array.push("                            </span>                        </a>                    ")):(_template_fun_array.push('                        <a href="javascript:;" class="btn-add-zf Jbtn_choose ',"undefined"==typeof(2==i||3==i?"Jchoose_skill":"")?"":baidu.template._encodeHTML(2==i||3==i?"Jchoose_skill":""),'" data-loc="',"undefined"==typeof data_attr[k]?"":baidu.template._encodeHTML(data_attr[k]),"_","undefined"==typeof i?"":baidu.template._encodeHTML(i),'" data-type="jineng_list.html" '),data[data_attr[k]][i]&&_template_fun_array.push(' data-id="',"undefined"==typeof data[data_attr[k]][i].iconId?"":baidu.template._encodeHTML(data[data_attr[k]][i].iconId),'" data-cost="',"undefined"==typeof data[data_attr[k]][i].cost?"":baidu.template._encodeHTML(data[data_attr[k]][i].cost),'" data-name="',"undefined"==typeof data[data_attr[k]][i].name?"":baidu.template._encodeHTML(data[data_attr[k]][i].name),'" data-bing="',"undefined"==typeof data[data_attr[k]][i].bing?"":baidu.template._encodeHTML(data[data_attr[k]][i].bing),'"'),_template_fun_array.push('>                            <i class="pj-txt">',"undefined"==typeof zhanfa[i-1]?"":baidu.template._encodeHTML(zhanfa[i-1]),'</i>                            <span class="pj-img-box">                                '),data[data_attr[k]][i]&&(_template_fun_array.push('                                    <img src="https://stzb.res.netease.com/pc/qt/20170323200251/data/jineng/tactics_0',"undefined"==typeof data[data_attr[k]][i].bing?"":baidu.template._encodeHTML(data[data_attr[k]][i].bing),'.png"/>                                    <span class="name-txt">',"undefined"==typeof data[data_attr[k]][i].name?"":data[data_attr[k]][i].name,"</span>                                    "),1!=i&&_template_fun_array.push('                                    <i class="btn-item-close Jitem_close"></i>                                    '),_template_fun_array.push("                                ")),_template_fun_array.push("                            </span>                        </a>                    ")),_template_fun_array.push("                ");_template_fun_array.push("                    </div>    ")}_template_fun_array.push("")}_template_fun_array.push(""),_template_varName=null}(_template_object);return fn=null,_template_fun_array.join("")}][0],html=under({data:data,data_attr:data_attr,jineng_type:jineng_type}),choose_target=$.cookie("stzb_pjmn_target");if(data){$("#pj-heros").html(html);for(var item in data)for(var i=0,len=data[item].length;len>i;i++)data[item][i]&&data[item][i].cost&&""!=data[item][i].cost&&(cur_cost+=1*data[item][i].cost);console.log(cur_cost)}console.log(data),info&&($("#pj_title").val(decodeURIComponent(info.tit)),$("#pj_desc").val(decodeURIComponent(info.desc))),choose_target&&(location.href.match("jineng_list.html")&&choose_target.split("_")[1]>0||location.href.match("card_list.html")&&0==choose_target.split("_")[1])&&($choose_target=$('.Jbtn_choose[data-loc="'+choose_target+'"]'))}function creatGroup(){$(".btn-pj-submit").click(function(){var t=$.trim($("#pj_title").val()),a=$.trim($("#pj_desc").val());t?a?$('.Jbtn_choose[data-loc="dy_0"]').attr("data-id")?_db.sensitive_test({text:t+a},function(e){e.passed?submitGroup({title:t,sdesc:a,simu_info:JSON.stringify(getData())}):alert("\u60a8\u7684\u8a00\u8bba\u6d89\u53ca\u654f\u611f\u8bcd\u6c47")}):alert("\u8bf7\u9009\u62e9\u5927\u8425\u6b66\u5c06"):alert("\u8bf7\u8f93\u5165\u63cf\u8ff0"):alert("\u8bf7\u8f93\u5165\u6807\u9898")})}function submitGroup(t){_db.submit_group(t,function(t){var a=t.status;1==a?($(".Jbtn_choose").removeAttr("data-id").removeAttr("data-name"),$(".Jbtn_choose .pj-img-box").html(""),$choose_box.attr("data-choose",0),closePop(),$("#pj_title").val(""),$("#pj_desc").val(""),alert("\u63d0\u4ea4\u6210\u529f"),$.cookie("stzb_pjmn_data",null,{expires:-1}),$.cookie("stzb_pjmn_target",null,{expires:-1}),$.cookie("stzb_pjmn_info",null,{expires:-1}),cur_cost=0):"\u8bf7\u5148\u767b\u5f55"==t.msg?showPop("pj_login"):alert(t.msg)})}function init(){var t=getQueryString("from"),a=$.cookie("stzb_pjmn_target");decodeData(),"pjmn"==t&&(a&&(location.href.match("jineng_list.html")&&a.split("_")[1]>0||location.href.match("card_list.html")&&0==a.split("_")[1])?$choose_box.attr("data-choose",1):showPj()),addEvent(),chooseEvent(),loginInit(),creatGroup(),$("#btn_mnpj").attr("href",pjmn_url)}function loginInit(){Login.create({holder:"#login_box",css:"https://stzb.res.netease.com/pc/gw/20230821163204/css/login_d15e2a2.css",logintype:"both",success:function(){Login.fn.isLogin(function(t){t&&showPop("pop_new")})}})}var $choose_box=$(".Jchoose_box"),data_attr=["dy","zy","qf"],$choose_target,jineng_type=["","&#25351;&#25381;","&#20027;&#21160;","&#34987;&#21160;","&#36861;&#20987;"],bing_type=["","&#27493;","&#39569;","&#24339;"],Login=nie.require("nie.util.login2"),cur_cost=0,_db=nie.require("DB"),pjmn_url="//stzb.163.com/heroteam",except=["200810","200881","200840","200841","200842"];init()});