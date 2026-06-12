# thesis-assets 璁烘枃鏉愭枡鎬荤储寮?

> **馃數 鏍稿績绾㈢嚎澹版槑**锛歚chapters/*.md` 鏄叏鍗锋鏂囩殑**鍞竴鐪熺浉婧愶紙Source of Truth锛?*銆?
> 浠讳綍瀵硅鏂囨鏂囩殑澧炲垹鏀瑰繀椤诲湪姝ゅ杩涜銆傛垜浠凡缁忓叏閲忛儴缃?PaperSpine 鑷姩鍖栭噸鍐欏紩鎿庝笌 Pandoc 娓叉煋寮曟搸锛屼紶缁熺殑灞€閮ㄦ墜宸ョ淮鎶ゆā寮忓凡褰诲簳搴熷純銆?

## 褰撳墠缁堟瀬鐩綍鎷撴墤

```text
thesis-assets/
鈹溾攢鈹€ README.md                    鈫?鏈枃浠讹細鏇存柊鍚庣殑鍏ㄥ眬绱㈠紩
鈹溾攢鈹€ paperspine-workflow.md       鈫?绠＄嚎璋冨害瑙勮寖锛氬畾涔夋墍鏈夌殑 PaperSpine 琛屼负涓庨噸鍐欐矙鐩掕竟鐣?
鈹溾攢鈹€ thesis-full.md               鈫?鍏ㄤ功鐗╃悊鍚堝苟鐗堬細鐢卞崟绔犵粍鍚堣€屾垚锛屾槸 Pandoc 娓叉煋鐨勭洿鎺ヨ緭鍏ユ簮
鈹溾攢鈹€ chapters/                    鈫?馃數 鍞竴鐪熺浉婧愶細鍖呭惈浠庢憳瑕佸埌绗叚绔犵殑 7 涓渶缁堝畾绋挎枃浠?
鈹溾攢鈹€ evidence/                    鈫?璇佹嵁鏉愭枡锛氱郴缁熸埅鍥俱€佹棩蹇椼€佷唬鐮佹锛堜緵 PaperSpine 璇诲彇锛?
鈹溾攢鈹€ literature/                  鈫?鏂囩尞绠＄悊锛氭枃鐚寘涓庡紩鐢ㄩ敋鐐?
鈹溾攢鈹€ defense/                     鈫?绛旇京鏉愭枡锛氱嫭绔嬬殑绛旇京婕旂粌銆丳PT 鏄犲皠涓庢紨璁茬褰掓。
鈹溾攢鈹€ meta/                        鈫?绠＄悊鏂囦欢涓庢帓鐗堟ā鏉匡細鍖呭惈鏋佸叾閲嶈鐨?school-template.docx
鈹?  鈹斺攢鈹€ workflow-governance.md   鈫?璁烘枃鐢熷懡鍛ㄦ湡涓庡伐鍏疯亴璐ｆ不鐞嗚鑼?
鈹溾攢鈹€ current/
鈹?  鈹斺攢鈹€ thesis-final.docx        鈫?DOCX 鑷姩鏋勫缓宸ヤ綔绋匡細鐢?thesis-full.md 涓庢瘝鐗堢粨鍚堢敓鎴愶紝涓嶈兘鐩存帴鎻愪氦
鈹斺攢鈹€ archive/                     鈫?鍘嗗彶褰掓。锛堜粎渚涘彧璇诲洖婧紝缁濅笉鍙備笌褰撳墠鐢熸垚娴侊級
    鈹溾攢鈹€ legacy/                  鈫?娣樻卑鐨勮€佹棫浣撶郴
    鈹?  鈹溾攢鈹€ drafts-original/
    鈹?  鈹溾攢鈹€ thesis-polished.md   鈫?鍘嗗彶鏃х増鐨勫畬鏁?Markdown
    鈹?  鈹溾攢鈹€ thesis-control.md    鈫?宸茶 paperspine-workflow.md 鍙栦唬鐨勬棫鎬绘帶
    鈹?  鈹斺攢鈹€ thesis-handbook/     鈫?鏇剧粡鐨勬墜宸ヤ綔鍧婂紡 Prompt 鏁欑▼锛?4 涓枃浠跺凡鍏ㄩ儴闄懍锛?
    鈹斺攢鈹€ matrices/                鈫?鍘嗘閲嶅啓鐭╅樀锛圧ationale Matrices锛?
```

淇敼浠讳綍璁烘枃璧勪骇鍓嶏紝蹇呴』鍏堥槄璇?meta/workflow-governance.md銆?
workflow-governance.md 鏄鏂囪祫浜ф不鐞嗘渶楂樿鑼冦€?

## 浜や粯涓庢覆鏌撶绾挎寚鍗?

**1. 淇敼鍐呭**
璇锋案杩滃湪 `chapters/` 鐩綍涓畾浣嶅埌瀵瑰簲鐨?`.md` 鏂囦欢杩涜鏂囨湰淇銆?

**2. 寮曞叆璇佹嵁澶т慨**
鑻ヨ繘琛屼簡浠ｇ爜閲嶅ぇ鍗囩骇锛屽皢浠ｇ爜鐗囨鏀惧叆 `evidence/`锛岄殢鍚庡湪纭璇佹嵁鏃犺鍚庯紝鍚姩 Agent 鑷姩鎵ц `rewrite_existing`锛堝綋鍓嶈鏂囨祦绋嬪叆鍙ｄ负 `paperspine-workflow.md`銆傝嫢鏈潵闇€瑕佸姩鎬佽皟搴︽枃浠讹紝鍙彟寤?`paperspine-execution-plan.md`锛涘綋鍓嶄粨搴撲笉渚濊禆璇ユ枃浠讹級銆?

**3. 鐢熸垚 DOCX 宸ヤ綔绋?*
```powershell
pwsh -ExecutionPolicy Bypass -File .\thesis-assets\build-docx.ps1
```
璇ュ懡浠ゅ彧鐢熸垚 current/thesis-final.docx 宸ヤ綔绋裤€傛彁浜ょ増 DOCX/PDF 蹇呴』鍦ㄥ唴瀹瑰喕缁撱€佸紩鐢ㄥ喕缁撳悗鐢变汉宸ュ湪 Word/WPS 涓粓瀹′骇鐢熴€?
*(娉細鎵嬪伐 Pandoc 鍛戒护浠呯敤浜庢帓鏌ワ紝涓嶄綔涓烘帹鑽愬叆鍙ｃ€傛寮忓叆鍙ｄ互 build-docx.ps1 涓哄噯銆?*

**4. 缁堟瀬浜哄伐妫€鏌ワ紙Last 5 Miles锛?*
- 鎵撳紑 `current\thesis-final.docx`銆?
- 灏嗘棫鐗堥仐鐣欑殑鍥剧墖鎸夐渶鎵嬪伐绮樿创鑷冲搴旀枃鏈銆?
- 绮樿创鍙傝€冩枃鐚笌闄勫綍銆?
- 鍙抽敭鏇存柊鍏ㄩ儴鐩綍锛屽苟鎻掑叆搴曠椤电爜銆?

**5. PDF 璺嚎璇存槑**
褰撳墠闃舵鍙嚜鍔ㄧ敓鎴?DOCX銆侾DF 寤鸿鍦?Word/WPS 涓畬鎴愮洰褰曞煙鏇存柊銆侀〉鐪夐〉鑴氥€侀〉鐮併€佸浘棰樿〃棰樹汉宸ョ粓瀹″悗瀵煎嚭銆傛殏涓嶅紩鍏?Pandoc-LaTeX PDF 鑷姩閾捐矾锛岄伩鍏嶄腑鏂囨ā鏉裤€侀〉鐪夐〉鑴氬拰瀛︽牎鏍煎紡澶辩湡銆?

