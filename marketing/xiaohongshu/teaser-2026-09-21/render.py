from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math
import random

OUT = Path(__file__).parent
W, H = 1080, 1440
CREAM = '#F5F3EC'
INK = '#173F35'
GREEN = '#286956'
MINT = '#C8E5D4'
ORANGE = '#F6B680'
LILAC = '#DDD5ED'
MUTED = '#61756C'
WHITE = '#FFFFFF'
FONT = 'C:/Windows/Fonts/Noto Sans SC (TrueType).otf'
BOLD = 'C:/Windows/Fonts/Noto Sans SC Bold (TrueType).otf'

def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)

def color(value):
    return tuple(int(value[i:i+2], 16) for i in (1, 3, 5))

def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius, fill=fill, outline=outline, width=width)

def text(draw, xy, content, size, fill=INK, bold=False, spacing=8):
    draw.multiline_text(xy, content, font=font(size, bold), fill=fill, spacing=spacing)

def centered(draw, y, content, size, fill=INK, bold=False):
    box = draw.textbbox((0, 0), content, font=font(size, bold))
    text(draw, ((W-(box[2]-box[0]))/2, y), content, size, fill, bold)

def card(base, box, radius=38, fill=WHITE, shadow=True):
    if shadow:
        layer = Image.new('RGBA', base.size)
        d = ImageDraw.Draw(layer)
        d.rounded_rectangle((box[0]+4, box[1]+10, box[2]+4, box[3]+14), radius, fill=(25, 65, 48, 30))
        base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(22)))
    d = ImageDraw.Draw(base)
    rounded(d, box, radius, fill)
    return d

def capsule(draw, x, y, label, bg, fg, size=26, pad=22, height=58):
    f = font(size, True)
    b = draw.textbbox((0,0), label, font=f)
    width = b[2]-b[0]+2*pad
    rounded(draw, (x,y,x+width,y+height), height//2, bg)
    draw.text((x+pad,y+(height-size)/2-3), label, font=f, fill=fg)
    return width

def wheat(draw, x, y, scale=1, stroke=INK):
    # Small original wheat mark, not a screenshot or copied logo.
    width = max(3, int(5*scale))
    draw.line((x, y+85*scale, x, y-72*scale), fill=stroke, width=width)
    for i in range(5):
        yy = y-52*scale+i*25*scale
        dx = (28-i*2)*scale
        draw.ellipse((x-dx-14*scale, yy-17*scale, x-8*scale, yy+12*scale), fill=stroke)
        draw.ellipse((x+8*scale, yy-17*scale, x+dx+14*scale, yy+12*scale), fill=stroke)
    draw.ellipse((x-9*scale,y-88*scale,x+9*scale,y-63*scale), fill=stroke)

def pin(draw, cx, cy, size=36, fill=GREEN):
    draw.ellipse((cx-size/2,cy-size/2,cx+size/2,cy+size/2), fill=fill)
    draw.polygon([(cx-size*.3,cy+size*.24),(cx+size*.3,cy+size*.24),(cx,cy+size*.72)], fill=fill)
    draw.ellipse((cx-size*.16,cy-size*.16,cx+size*.16,cy+size*.16), fill=WHITE)

def basic(index, inverse=False):
    im = Image.new('RGBA',(W,H), color(INK if inverse else CREAM)+(255,))
    d=ImageDraw.Draw(im)
    random.seed(index)
    if inverse:
        d.ellipse((-260,800,480,1540), fill='#205342')
        d.ellipse((720,-180,1310,470), fill='#265E4D')
    else:
        d.ellipse((700,-220,1380,420), fill='#E8E9D7')
        d.ellipse((-350,1120,270,1750), fill='#E4EBDE')
    for _ in range(75):
        x=random.randint(20,W-20); y=random.randint(20,H-20)
        d.ellipse((x,y,x+2,y+2), fill=('#4E7B69' if inverse else '#DAE3D9'))
    fg=WHITE if inverse else INK
    wheat(d, 78, 88, .28, ORANGE if inverse else GREEN)
    text(d,(118,52),'麦穗旅序',30,fg,True)
    text(d,(75,1344),'MAISUI TRAVEL  ·  开发中',21,('#B8D7C4' if inverse else MUTED),True)
    text(d,(950,1337),f'{index:02d} / 04',23,('#B8D7C4' if inverse else MUTED),True)
    return im

def page1():
    im=basic(1)
    d=ImageDraw.Draw(im)
    capsule(d,76,155,'一款正在长大的旅行 App',MINT,GREEN,24,24,54)
    text(d,(70,259),'把期待，',92,INK,True)
    text(d,(70,365),'排进日历。',92,INK,True)
    text(d,(75,506),'计划行程，也收藏沿途的回忆。',34,MUTED)
    # Friendly route curl behind the floating application panel.
    d.arc((560,450,1130,1090),45,275,fill='#AECDB9',width=7)
    pin(d,805,556,34,ORANGE)
    card(im,(88,610,989,1217),58)
    d=ImageDraw.Draw(im)
    rounded(d,(125,647,952,736),26,'#EAF3EB')
    text(d,(155,667),'周末去看看海  ☀',36,INK,True)
    text(d,(126,778),'DAY 01',25,GREEN,True)
    d.line((176,843,176,1112),fill='#BBD7C3',width=5)
    entries=[('09:00','出发，去见想看的风景'),('13:30','把喜欢的地方排进行程'),('18:20','留住今天的日落')]
    for n,(time,label) in enumerate(entries):
        y=824+n*132
        d.ellipse((165,y+18,187,y+40),fill=GREEN if n==0 else ORANGE)
        text(d,(220,y),time,27,GREEN,True)
        text(d,(220,y+43),label,29,INK)
    capsule(d,91,1250,'先认识一下它  →',ORANGE,INK,26,28,60)
    im.convert('RGB').save(OUT/'01-cover.png',quality=96)

def page2():
    im=basic(2)
    d=ImageDraw.Draw(im)
    capsule(d,76,160,'01  旅行规划',MINT,GREEN,25)
    text(d,(70,262),'收藏夹里的地点，',74,INK,True)
    text(d,(70,357),'终于有了顺序。',74,INK,True)
    text(d,(76,482),'按旅行整理行程，日期、时间、交通和地点信息',29,MUTED)
    text(d,(76,531),'放在一起，出发前不用来回翻找。',29,MUTED)
    card(im,(74,631,1005,1175),52)
    d=ImageDraw.Draw(im)
    text(d,(112,674),'成都 · 慢慢逛的两天',43,INK,True)
    text(d,(112,737),'6 月 15 日   星期日',25,MUTED)
    d.line((151,816,151,1101),fill='#B9D8C4',width=5)
    entries=[('09:30','早午餐','美食 · 约 1 小时'),('12:00','宽窄巷子','人文 · 步行 15 分钟'),('17:30','去看傍晚的城市','风景 · 地图可搜索')]
    for i,(tm,name,note) in enumerate(entries):
        y=795+i*120
        d.ellipse((140,y+12,162,y+34),fill=GREEN if i<2 else ORANGE)
        text(d,(193,y-4),tm,27,GREEN,True)
        text(d,(318,y-6),name,31,INK,True)
        text(d,(318,y+37),note,23,MUTED)
    capsule(d,78,1210,'旅行 / 行程 / 地点，一页页理清楚',LILAC,INK,25,24,65)
    im.convert('RGB').save(OUT/'02-plan.png',quality=96)

def scene(draw, box, sky, hill, sun):
    x1,y1,x2,y2=box
    rounded(draw,box,28,sky)
    draw.ellipse((x2-145,y1+42,x2-82,y1+105),fill=sun)
    draw.polygon([(x1,y2-70),(x1+100,y1+155),(x1+220,y2-88),(x2-125,y1+134),(x2,y2-80),(x2,y2),(x1,y2)],fill=hill)
    draw.polygon([(x1,y2-18),(x1+90,y2-77),(x1+220,y2-30),(x2-70,y2-92),(x2,y2-42),(x2,y2),(x1,y2)],fill='#357768')

def page3():
    im=basic(3)
    d=ImageDraw.Draw(im)
    capsule(d,76,160,'02  旅行打卡', '#FBE3CC', '#A35C2D',25)
    text(d,(70,262),'走过的路，',83,INK,True)
    text(d,(70,366),'也值得好好收好。',75,INK,True)
    text(d,(76,483),'照片、文字和地点，按每段旅行整理成回忆。',29,MUTED)
    card(im,(73,597,1005,1180),52)
    d=ImageDraw.Draw(im)
    text(d,(113,635),'夏天的旅行  ·  3 条打卡',34,INK,True)
    scene(d,(111,710,526,1038),'#D3E8DD','#93B6AC','#FFE3A3')
    scene(d,(550,710,966,1038),'#E3E0F0','#AAA3C4','#F7C99E')
    text(d,(129,1053),'海边的风',26,INK,True)
    text(d,(568,1053),'山顶的晚霞',26,INK,True)
    text(d,(113,1111),'原图保留，也能自由裁剪、旋转和涂画。',24,MUTED)
    capsule(d,78,1214,'攻略可以分享，回忆也可以留给自己',MINT,GREEN,25,23,63)
    im.convert('RGB').save(OUT/'03-memory.png',quality=96)

def page4():
    im=basic(4,True)
    d=ImageDraw.Draw(im)
    capsule(d,76,160,'03  自己的旅行方式', '#D9EADB',INK,25)
    text(d,(70,266),'一切都可以',84,WHITE,True)
    text(d,(70,371),'更像你。',91,'#F8C59A',True)
    text(d,(76,514),'标签自己改，颜色自己选。',33,'#CDE2D3')
    text(d,(76,566),'旅行计划不必长得一模一样。',33,'#CDE2D3')
    card(im,(74,669,1005,1123),49, '#F8F7F2')
    d=ImageDraw.Draw(im)
    text(d,(113,703),'我的旅行标签',38,INK,True)
    tags=[('人文','#E4DDF2'),('风景','#CCE6D8'),('美食','#FBD9BF'),('散步','#EDE8CF'),('日落','#D9E2F4')]
    positions=[(113,782),(355,782),(596,782),(113,878),(355,878)]
    for (label,bg),(x,y) in zip(tags,positions): capsule(d,x,y,label,bg,INK,29,31,66)
    text(d,(113,1039),'预设只是起点，分组和标签都能修改。',23,MUTED)
    rounded(d,(74,1173,1005,1282),31,'#F6BD8D')
    centered(d,1193,'麦穗旅序  ·  Android 开发中',37,INK,True)
    im.convert('RGB').save(OUT/'04-coming-soon.png',quality=96)

if __name__ == '__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    page1(); page2(); page3(); page4()
    for p in sorted(OUT.glob('0*.png')):
        with Image.open(p) as image:
            print(p.name, image.size, p.stat().st_size)
